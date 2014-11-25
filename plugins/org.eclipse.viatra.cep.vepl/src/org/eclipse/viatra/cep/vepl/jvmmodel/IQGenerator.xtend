/*******************************************************************************
 * Copyright (c) 2004-2014, Istvan David, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Istvan David - initial API and implementation
 *******************************************************************************/
package org.eclipse.viatra.cep.vepl.jvmmodel

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.google.inject.Inject
import java.util.List
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.incquery.patternlanguage.patternLanguage.Pattern
import org.eclipse.incquery.runtime.api.IMatchProcessor
import org.eclipse.incquery.runtime.evm.specific.event.IncQueryActivationStateEnum
import org.eclipse.incquery.runtime.exception.IncQueryException
import org.eclipse.viatra.cep.core.streams.EventStream
import org.eclipse.viatra.cep.vepl.vepl.EventModel
import org.eclipse.viatra.cep.vepl.vepl.IQPatternChangeType
import org.eclipse.viatra.cep.vepl.vepl.IQPatternEventPattern
import org.eclipse.viatra.cep.vepl.vepl.PatternUsage
import org.eclipse.viatra.cep.vepl.vepl.TypedParameter
import org.eclipse.viatra2.emf.runtime.rules.eventdriven.EventDrivenTransformationRuleFactory
import org.eclipse.viatra2.emf.runtime.rules.eventdriven.EventDrivenTransformationRuleFactory.EventDrivenTransformationBuilder
import org.eclipse.viatra2.emf.runtime.transformation.eventdriven.EventDrivenTransformation
import org.eclipse.viatra2.emf.runtime.transformation.eventdriven.EventDrivenTransformationRule
import org.eclipse.viatra2.emf.runtime.transformation.eventdriven.InconsistentEventSemanticsException
import org.eclipse.xtext.xbase.compiler.output.ITreeAppendable
import org.eclipse.xtext.xbase.jvmmodel.IJvmDeclaredTypeAcceptor
import org.eclipse.xtext.xbase.jvmmodel.JvmTypesBuilder
import org.eclipse.xtext.common.types.JvmVisibility

@SuppressWarnings("restriction","discouraged")
class IQGenerator {

	@Inject extension JvmTypesBuilder jvmTypesBuilder
	@Inject extension Utils
	@Inject extension NamingProvider

	def void generateIncQuery2ViatraCep(List<IQPatternEventPattern> patterns, EventModel model,
		IJvmDeclaredTypeAcceptor acceptor) {
		if (model.packagedModel.usages.filter[e|(e instanceof PatternUsage)].size == 0) {
			return
		}

		val fqn = patterns.head.iq2CepClassFqn
		acceptor.accept(model.toClass(fqn)).initializeLater [
			documentation = model.documentation
			members += model.toField("eventStream", model.newTypeRef(EventStream))
			members += model.toField("resourceSet", model.newTypeRef(ResourceSet))
			members += model.toField("transformation", model.newTypeRef(EventDrivenTransformation))
			var constructor = model.toConstructor [
				parameters += toParameter("resourceSet", model.newTypeRef(ResourceSet))
				parameters += toParameter("eventStream", model.newTypeRef(EventStream))
				body = [
					append(
						'''
						this.resourceSet = resourceSet;
						this.eventStream = eventStream;
						registerRules();'''
					)
				]
			]
			constructor.setVisibility(JvmVisibility.PRIVATE)
			members += constructor
			val groupedPatterns = groupEventPatternsByIqPatternRef(patterns)
			var registerMappingMethod = model.toMethod("register", model.newTypeRef(fqn.toString)) [
				parameters += toParameter("resourceSet", model.newTypeRef(ResourceSet))
				parameters += toParameter("eventStream", model.newTypeRef(EventStream))
				body = [
					append(
						'''
						return new IncQuery2ViatraCep(resourceSet, eventStream);'''
					)
				]
			]
			registerMappingMethod.setVisibility(JvmVisibility.PUBLIC)
			registerMappingMethod.setStatic(true)
			members += registerMappingMethod
			members += model.toMethod("getRules",
				model.newTypeRef("org.eclipse.viatra2.emf.runtime.rules.EventDrivenTransformationRuleGroup")) [
				body = [
					append(
						'''
						EventDrivenTransformationRuleGroup ruleGroup = new EventDrivenTransformationRuleGroup(
							«FOR p : groupedPatterns.keySet SEPARATOR ", " AFTER ");"»
								«p.mappingMethodName»()
							«ENDFOR»
						
						return ruleGroup;'''
					)
				]
			]
			var registerTransformationMethod = model.toMethod("registerRules", model.newTypeRef(void)) [
				body = [
					append(
						'''
						transformation = EventDrivenTransformation.forResource(resourceSet).addRules(getRules()).create();'''
					)
				]
			]
			registerTransformationMethod.setVisibility(JvmVisibility.PRIVATE)
			members += registerTransformationMethod
			val patternsNamespace = model.packagedModel.usages.filter[e|(e instanceof PatternUsage)].head.
				importedNamespace.replace('*', '')
			for (p : groupedPatterns.keySet) {
				if (p != null) {
					val matcher = patternsNamespace + p.name.toFirstUpper + "Matcher"
					val match = patternsNamespace + p.name.toFirstUpper + "Match"

					members += model.toMethod(p.mappingMethodName,
						model.newTypeRef(EventDrivenTransformationRule, model.newTypeRef(match),
							model.newTypeRef(matcher))) [
						body = [
							append('''try{''').increaseIndentation
							newLine
							append(
								'''«referClass(it, p, EventDrivenTransformationBuilder, model.newTypeRef(match),
									model.newTypeRef(matcher))»''')
							append(''' builder = new ''')
							append('''«referClass(it, p, EventDrivenTransformationRuleFactory)»''')
							append('''().createRule();''')
							newLine
							append(
								'''
								builder.addLifeCycle(EventDrivenTransformationRuleFactory.INTERVAL_SEMANTICS);
								builder.precondition(''').append('''«it.referClass(matcher, p)»''').append(
								'''.querySpecification());
									''')
							for (eventPattern : groupedPatterns.get(p)) {
								newLine
								append(
									'''«referClass(it, eventPattern, IMatchProcessor, model.newTypeRef(match))» «eventPattern.
										actionName»''').append(''' = new ''').append(
									'''«referClass(it, eventPattern, IMatchProcessor, model.newTypeRef(match))»() {''').
									increaseIndentation
								newLine
								append('''public void process(final ''').append('''«it.referClass(match, p)»''').
									append(''' matchedPattern) {''').increaseIndentation
								newLine
								append('''«it.referClass(eventPattern.classFqn, p)»''').append(''' event = new ''').
									append('''«it.referClass(eventPattern.classFqn, p)»''').append('''(null);''')
								append('''«getParameterMapping(it, eventPattern)»''')
								newLine
								append('''event.setIncQueryPattern(matchedPattern);''')
								newLine
								append('''eventStream.push(event);''').decreaseIndentation
								newLine
								append('''}''').decreaseIndentation
								newLine
								append('''};''')
								newLine
								append('''builder.action(''').append(
									'''«referClass(it, eventPattern, IncQueryActivationStateEnum)».''').append(
									'''«eventPattern.getActivationState», «eventPattern.actionName»''').append(
									''');''')
								newLine
							}
							newLine
							append('''return builder.build();''').decreaseIndentation
							newLine
							append('''} catch (''').append('''«referClass(it, p, IncQueryException)» e) {''').
								increaseIndentation
							newLine
							append('''e.printStackTrace();''').decreaseIndentation
							newLine
							append('''} catch (''').append(
								'''«referClass(it, p, InconsistentEventSemanticsException)»''').append(''' e) {''').
								increaseIndentation
							newLine
							append('''e.printStackTrace();''').decreaseIndentation
							newLine
							append('''}''')
							newLine
							append(
								'''return null;'''
							)
						]
					]
				}
			}
			var disposeMethod = model.toMethod("dispose", model.newTypeRef("void")) [
				body = [
					append(
						'''
						this.transformation = null;'''
					)
				]
			]
			members += disposeMethod
		]
	}

	def private getMappingMethodName(Pattern pattern) {
		return "create" + pattern.name + "_MappingRule"
	}

	def private groupEventPatternsByIqPatternRef(List<IQPatternEventPattern> eventPatterns) {
		var Multimap<Pattern, IQPatternEventPattern> groupedPatterns = ArrayListMultimap.create();

		for (p : eventPatterns) {
			if (p.iqPatternRef != null) {
				var iqPattern = p.iqPatternRef.iqpattern
				groupedPatterns.put(iqPattern, (p as IQPatternEventPattern))
			}
		}

		return groupedPatterns
	}

	def private getActivationState(IQPatternEventPattern eventPattern) {
		switch (eventPattern.iqChangeType) {
			case IQPatternChangeType.NEW_MATCH_FOUND: return IncQueryActivationStateEnum.APPEARED
			case IQPatternChangeType.EXISTING_MATCH_LOST: return IncQueryActivationStateEnum.DISAPPEARED
		}
	}

	def private getActionName(IQPatternEventPattern eventPattern) {
		switch (eventPattern.iqChangeType) {
			case IQPatternChangeType.NEW_MATCH_FOUND: return "actionOnAppear"
			case IQPatternChangeType.EXISTING_MATCH_LOST: return "actionOnDisappear"
		}
	}

	def private getParameterMapping(ITreeAppendable appendable, EObject ctx) {
		var params = (ctx as IQPatternEventPattern).parameters
		if (params == null) {
			return
		}
		var eventPatternParams = params.parameters
		var iqPatternParams = (ctx as IQPatternEventPattern).iqPatternRef.parameterList.parameters

		var i = -1;
		while ((i = i + 1) < iqPatternParams.size) {
			var iqParamName = iqPatternParams.get(i).name
			var eventParamPosition = getEventParamPosition(iqParamName, eventPatternParams)
			if (!(iqParamName.startsWith("_"))) {
				var eventParamType = eventPatternParams.get(eventParamPosition).type
				appendable.append(
					'''
					event.set«iqParamName.toFirstUpper»((''').append('''«eventParamType.qualifiedName»''').append(
					''')matchedPattern.get(«i»));
						''')
			}
		}
	}

	def private getEventParamPosition(String iqParamName, List<TypedParameter> eventPatternParams) {
		var i = 0
		for (ep : eventPatternParams) {
			if (ep.name.equals(iqParamName)) {
				return i
			}
			i = i + 1
		}
	}

}

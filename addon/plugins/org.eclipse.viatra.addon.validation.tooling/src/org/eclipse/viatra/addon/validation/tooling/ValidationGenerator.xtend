/*******************************************************************************
 * Copyright (c) 2010-2012, Zoltan Ujhelyi, Abel Hegedus, Tamas Szabo, Istvan Rath and Daniel Varro
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-v20.html.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.viatra.addon.validation.tooling

import com.google.inject.Inject
import org.eclipse.core.runtime.Path
import org.eclipse.viatra.query.patternlanguage.emf.vql.PatternModel
import org.eclipse.viatra.query.patternlanguage.emf.jvmmodel.EMFPatternLanguageJvmModelInferrerUtil
import org.eclipse.viatra.query.patternlanguage.emf.vql.Annotation
import org.eclipse.viatra.query.patternlanguage.emf.vql.ListValue
import org.eclipse.viatra.query.patternlanguage.emf.vql.Pattern
import org.eclipse.viatra.query.patternlanguage.emf.vql.StringValue
import org.eclipse.viatra.query.patternlanguage.emf.vql.ValueReference
import org.eclipse.viatra.query.patternlanguage.emf.vql.VariableReference
import org.eclipse.viatra.query.tooling.core.generator.ExtensionGenerator
import org.eclipse.viatra.query.tooling.core.generator.fragments.IGenerationFragment
import org.eclipse.viatra.query.tooling.core.generator.genmodel.IVQGenmodelProvider
import org.eclipse.xtext.generator.IFileSystemAccess
import org.eclipse.xtext.util.Strings

import static extension org.eclipse.viatra.query.patternlanguage.emf.helper.PatternLanguageHelper.*

class ValidationGenerator implements IGenerationFragment {

    @Inject extension EMFPatternLanguageJvmModelInferrerUtil

    @Inject
    IVQGenmodelProvider vqGenModelProvider

    @Inject extension ExtensionGenerator exGen

    final static String VALIDATIONEXTENSION_PREFIX = "validation.constraint."
    final static String UI_VALIDATION_MENUS_PREFIX = "generated.viatra.addon.validation.menu."
    final static String VALIDATION_EXTENSION_POINT = "org.eclipse.viatra.addon.validation.runtime.constraint"
    final static String ECLIPSE_MENUS_EXTENSION_POINT = "org.eclipse.ui.menus"
    final static String ANNOTATION_NAME = "Constraint"

    override generateFiles(Pattern pattern, IFileSystemAccess fsa) {
        pattern.annotations.filter[name == ANNOTATION_NAME].forEach[ann |
            fsa.generateFile(pattern.constraintClassJavaFile(ann), pattern.patternHandler(ann))
        ]
    }

    override cleanUp(Pattern pattern, IFileSystemAccess fsa) {
        pattern.annotations.filter[name == ANNOTATION_NAME].forEach[ann |
            fsa.deleteFile(pattern.constraintClassJavaFile(ann))
        ]
    }

    override removeExtension(Pattern pattern) {
        val p = Pair::of(pattern.constraintContributionId, VALIDATION_EXTENSION_POINT)
        val extensionList = newArrayList(p)

        val patternModel = pattern.eContainer as PatternModel;
        for (imp : getPackageImportsIterable(patternModel)) {
            val pack = imp.EPackage;
            val genPackage = vqGenModelProvider.findGenPackage(pattern, pack);

            if (genPackage !== null) {
                val editorId = genPackage.qualifiedEditorClassName + "ID";
                if (!editorId.nullOrEmpty) {
                    extensionList.add(Pair::of(menuContributionId(editorId), ECLIPSE_MENUS_EXTENSION_POINT))
                }
            }
        }

        for (ann : pattern.annotations) {
            if (ann.name == ANNOTATION_NAME) {
                val editorIds = ann.getAnnotationParameterValue("targetEditorId")
                for (id : editorIds) {
                    val editorId = (id as StringValue).value
                    extensionList.add(Pair::of(menuContributionId(editorId), ECLIPSE_MENUS_EXTENSION_POINT))
                }
            }
        }
        return extensionList
    }

    override getRemovableExtensions() {
        newArrayList(
            Pair::of(VALIDATIONEXTENSION_PREFIX, VALIDATION_EXTENSION_POINT),
            Pair::of(UI_VALIDATION_MENUS_PREFIX, ECLIPSE_MENUS_EXTENSION_POINT)
        )
    }

    override getProjectDependencies() {
        newArrayList(
            "org.eclipse.viatra.query.runtime",
            "org.eclipse.viatra.addon.validation.core"
        )
    }

    override getProjectPostfix() {
        "validation"
    }

    override extensionContribution(Pattern pattern) {
        val extensionList = newArrayList(
            contribExtension(pattern.constraintContributionId, VALIDATION_EXTENSION_POINT) [
                for (ann : pattern.annotations) {
                    if (ann.name == ANNOTATION_NAME) {
                        contribElement(it, "constraint") [
                            contribAttribute(it, "class", pattern.constraintClassName(ann))
                            contribAttribute(it, "name", pattern.fullyQualifiedName)

                            val editorIds = ann.getAnnotationParameterValue("targetEditorId")
                            for (id : editorIds) {
                                val editorId = (id as StringValue).value
                                contribElement(it, "enabledForEditor") [
                                    contribAttribute(it, "editorId", editorId)
                                ]
                            }

                            val patternModel = pattern.eContainer as PatternModel;
                            for (imp : getPackageImportsIterable(patternModel)) {
                                val pack = imp.EPackage;
                                val genPackage = vqGenModelProvider.findGenPackage(pattern, pack);

                                if (genPackage !== null) {
                                    val editorId = genPackage.qualifiedEditorClassName + "ID";
                                    contribElement(it, "enabledForEditor") [
                                        contribAttribute(it, "editorId", editorId)
                                    ]
                                }
                            }
                        ]
                    }
                }
            ]
        )

        return extensionList
    }

    def constraintClassName(Pattern pattern, Annotation annotation) {
        String::format("%s.%s%s%s", pattern.packageName, pattern.realPatternName.toFirstUpper, ANNOTATION_NAME,
            pattern.annotations.indexOf(annotation))
    }

    def constraintClassPath(Pattern pattern, Annotation annotation) {
        String::format("%s/%s%s%s", pattern.packagePath, pattern.realPatternName.toFirstUpper, ANNOTATION_NAME,
            pattern.annotations.indexOf(annotation))
    }

    def constraintClassJavaFile(Pattern pattern, Annotation annotation) {
        pattern.constraintClassPath(annotation) + ".java"
    }

    def constraintContributionId(Pattern pattern) {
        return VALIDATIONEXTENSION_PREFIX + getFullyQualifiedName(pattern)
    }

    def menuContributionId(String editorId) {
        return String::format("%s%s", UI_VALIDATION_MENUS_PREFIX, editorId)
    }

    def getElementOfConstraintAnnotation(Annotation annotation, String elementName) {
        val ap = getFirstAnnotationParameter(annotation, elementName)
        return switch (ap) {
            StringValue: ap.value
            VariableReference: ap.^var
            default: null
        }
    }

    def getAnnotationParameterValue(Annotation annotation, String elementName) {
        val values = newArrayList()
        for (ap : annotation.parameters) {
            if (ap.name.matches(elementName)) {
                values.add(ap.value)
            }
        }
        return values
    }

    override getAdditionalBinIncludes() {
        return newArrayList(new Path("plugin.xml"))
    }

    def patternHandler(Pattern pattern, Annotation annotation) {
        val specificationType = pattern.findInferredSpecification
        val className = pattern.name.toFirstUpper + ANNOTATION_NAME + pattern.annotations.indexOf(annotation)
        '''
            /**
            «pattern.fileComment»
            */
            package «pattern.packageName»;
            
            import java.util.HashMap;
            import java.util.HashSet;
            import java.util.List;
            import java.util.Map;
            import java.util.Set;
            import java.util.Arrays;
            
            import org.eclipse.viatra.addon.validation.core.api.Severity;
            import org.eclipse.viatra.addon.validation.core.api.IConstraintSpecification;
            import org.eclipse.viatra.query.runtime.api.IPatternMatch;
            import org.eclipse.viatra.query.runtime.api.IQuerySpecification;
            import org.eclipse.viatra.query.runtime.api.ViatraQueryMatcher;
            
            import «specificationType.qualifiedName»;
            
            public class «className» implements IConstraintSpecification {
            
                private «specificationType.simpleName» querySpecification;
            
                public «className»() {
                    querySpecification = «specificationType.simpleName».instance();
                }
            
                @Override
                public String getMessageFormat() {
                    return "«getElementOfConstraintAnnotation(annotation, "message").isNullOrEmpty ? "" 
                        : Strings::convertToJavaString(getElementOfConstraintAnnotation(annotation, "message"))»";
                }
            
            
                @Override
                public Map<String,Object> getKeyObjects(IPatternMatch signature) {
                    Map<String,Object> map = new HashMap<>();
                    «FOR key : pattern.getKeyList(annotation)»
                        map.put("«key»",signature.get("«key»"));
                    «ENDFOR»
                    return map;
                }
            
                @Override
                public List<String> getKeyNames() {
                    List<String> keyNames = Arrays.asList(
                        «FOR key : pattern.getKeyList(annotation) SEPARATOR ","»
                            "«key»"
                        «ENDFOR»
                    );
                    return keyNames;
                }
            
                @Override
                public List<String> getPropertyNames() {
                    List<String> propertyNames = Arrays.asList(
                        «FOR property : pattern.getPropertyList(annotation) SEPARATOR ","»
                            "«property»"
                        «ENDFOR»
                    );
                    return propertyNames;
                }
            
                @Override
                public Set<List<String>> getSymmetricPropertyNames() {
                    Set<List<String>> symmetricPropertyNamesSet = new HashSet<>();
                    «val symmetricProperties = pattern.getSymmetricList(annotation).filter[
                        !pattern.getKeyList(annotation).containsAll(it)
                    ]»
                    «FOR propertyList : symmetricProperties»
                    symmetricPropertyNamesSet.add(Arrays.asList(«FOR property : propertyList SEPARATOR ","»"«property»"«ENDFOR»));
                    «ENDFOR»
                    return symmetricPropertyNamesSet;
                }
            
                @Override
                public Set<List<String>> getSymmetricKeyNames() {
                    Set<List<String>> symmetricKeyNamesSet = new HashSet<>();
                    «val symmetricKeys = pattern.getSymmetricList(annotation).filter[
                        pattern.getKeyList(annotation).containsAll(it)
                    ]»
                    «FOR symmetricKeyList : symmetricKeys»
                    symmetricKeyNamesSet.add(Arrays.asList(«FOR key : symmetricKeyList SEPARATOR ","»"«key»"«ENDFOR»));
                    «ENDFOR»
                    return symmetricKeyNamesSet;
                }
            
                @Override
                public Severity getSeverity() {
                    return Severity.«getElementOfConstraintAnnotation(annotation, "severity").toUpperCase»;
                }
            
                @Override
                public IQuerySpecification<? extends ViatraQueryMatcher<? extends IPatternMatch>> getQuerySpecification() {
                    return querySpecification;
                }
            
            }
        '''
    }

    def getKeyList(Pattern pattern, Annotation annotation) {
        val keyParamValues = (annotation.getFirstAnnotationParameter("key") as ListValue).values
        keyParamValues.map[it.nameOfParameterFromValueReference]
    }

    def getPropertyList(Pattern pattern, Annotation annotation) {
        val parameters = pattern.parameters.map[name]
        val keyParamValues = (annotation.getFirstAnnotationParameter("key") as ListValue).values
        val keys = keyParamValues.map[it.nameOfParameterFromValueReference]
        parameters.filter[!keys.contains(it)]
    }

    def getSymmetricList(Pattern pattern, Annotation annotation) {
        val symmetricParams = annotation.getAnnotationParameters("symmetric")
        symmetricParams.map [
            (it as ListValue).values.map [
                it.nameOfParameterFromValueReference
            ]
        ]
    }

    def String getNameOfParameterFromValueReference(ValueReference ref) {
        if (ref instanceof StringValue) {
            ref.value
        } else if (ref instanceof VariableReference) {
            ref.variable.name
        } else {
            ref.toString
        }
    }
}

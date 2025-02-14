/*******************************************************************************
 * Copyright (c) 2010-2012, Zoltan Ujhelyi, Istvan Rath and Daniel Varro
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-v20.html.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.viatra.query.patternlanguage.emf.tests.resolution

import com.google.inject.Inject
import org.eclipse.viatra.query.patternlanguage.emf.vql.PathExpressionConstraint
import org.eclipse.viatra.query.patternlanguage.emf.vql.PatternCompositionConstraint
import org.eclipse.viatra.query.patternlanguage.emf.vql.EClassifierConstraint
import org.eclipse.viatra.query.patternlanguage.emf.vql.PatternModel
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.XtextRunner
import org.eclipse.xtext.testing.util.ParseHelper
import org.eclipse.xtext.testing.validation.ValidationTestHelper
import org.junit.Test
import org.junit.runner.RunWith

import static org.junit.Assert.*
import org.eclipse.viatra.query.patternlanguage.emf.validation.IssueCodes
import org.eclipse.viatra.query.patternlanguage.emf.tests.CustomizedEMFPatternLanguageInjectorProvider
import org.eclipse.viatra.query.patternlanguage.emf.vql.VariableReference
import org.eclipse.viatra.query.patternlanguage.emf.helper.PatternLanguageHelper

@RunWith(typeof(XtextRunner))
@InjectWith(typeof(CustomizedEMFPatternLanguageInjectorProvider))
class VariableResolutionTest {
    @Inject
    ParseHelper<PatternModel> parseHelper
    
    @Inject extension ValidationTestHelper
    
    @Test
    def parameterResolution() {
        val model = parseHelper.parse('
            package org.eclipse.viatra.query.patternlanguage.emf.tests
            import "http://www.eclipse.org/viatra/query/patternlanguage/emf/PatternLanguage"

            pattern resolutionTest(Name) = {
                Pattern(Name);
            }
        ')
        model.assertNoErrors
        val pattern = model.patterns.get(0)
        val parameter = pattern.parameters.get(0)
        val constraint = pattern.bodies.get(0).constraints.get(0) as EClassifierConstraint
        assertEquals(parameter.name, constraint.getVar().getVar())
    }
    
    @Test
    def singleUseResolution() {
        val model = parseHelper.parse('
            package org.eclipse.viatra.query.patternlanguage.emf.tests
            import "http://www.eclipse.org/viatra/query/patternlanguage/emf/PatternLanguage"

            pattern resolutionTest(Name) = {
                Pattern.parameters(Name,_parameter);
            }
        ')
        
        model.assertNoErrors
        val pattern = model.patterns.get(0)
        val parameter = pattern.parameters.get(0)
        val constraint = pattern.bodies.get(0).constraints.get(0) as PathExpressionConstraint
        assertEquals(parameter.name, constraint.src.variable.name)
    }
    @Test
    def anonymVariablesResolution() {
        val model = parseHelper.parse('
            package org.eclipse.viatra.query.patternlanguage.emf.tests
            import "http://www.eclipse.org/viatra/query/patternlanguage/emf/PatternLanguage"
            pattern helper(A,B,C) = {
                Pattern(A);
                Pattern(B);
                Pattern(C);
            }
            pattern resolutionTest(A) = {
                find helper(A, _, _);
            }
        ')
        model.assertNoErrors
        val pattern = model.patterns.get(1)
        val constraint = pattern.bodies.get(0).constraints.get(0) as PatternCompositionConstraint
        assertNotSame((PatternLanguageHelper.getCallParameters(constraint.call).get(1) as VariableReference).variable.name,
            (PatternLanguageHelper.getCallParameters(constraint.call).get(2) as VariableReference).variable.name
        )
    }
    
    @Test
    def parameterResolutionFailed() {
        val model = parseHelper.parse('
            package org.eclipse.viatra.query.patternlanguage.emf.tests
            import "http://www.eclipse.org/viatra/query/patternlanguage/emf/PatternLanguage"

            pattern resolutionTest(Name) = {
                Pattern(Name2);
            }
        ')
        val pattern = model.patterns.get(0)
        val parameter = pattern.parameters.get(0)
        val constraint = pattern.bodies.get(0).constraints.get(0) as EClassifierConstraint
        model.assertError(parameter.eClass, IssueCodes::SYMBOLIC_VARIABLE_NEVER_REFERENCED)
        model.assertWarning(constraint.getVar().eClass, IssueCodes::LOCAL_VARIABLE_REFERENCED_ONCE)
        assertTrue(parameter.name != constraint.getVar().getVar())
    }
    
    @Test
    def constraintVariableResolution() {
        val model = parseHelper.parse('
            package org.eclipse.viatra.query.patternlanguage.emf.tests
            import "http://www.eclipse.org/viatra/query/patternlanguage/emf/PatternLanguage"

            pattern resolutionTest(Name) = {
                Pattern(Name2);
                Pattern(Name2);
            }
        ')
        val pattern = model.patterns.get(0)
        val parameter = pattern.parameters.get(0)
        model.assertError(parameter.eClass, IssueCodes::SYMBOLIC_VARIABLE_NEVER_REFERENCED)
        val constraint0 = pattern.bodies.get(0).constraints.get(0) as EClassifierConstraint
        val constraint1 = pattern.bodies.get(0).constraints.get(0) as EClassifierConstraint
        assertTrue(parameter.name != constraint0.getVar().getVar())
        assertEquals(constraint0.getVar().getVar(), constraint1.getVar().getVar())				
    }
}
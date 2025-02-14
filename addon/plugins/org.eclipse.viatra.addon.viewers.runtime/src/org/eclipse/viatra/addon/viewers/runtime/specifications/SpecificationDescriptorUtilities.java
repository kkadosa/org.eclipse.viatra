/*******************************************************************************
 * Copyright (c) 2010-2018, Zoltan Ujhelyi, IncQuery Labs Ltd.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-v20.html.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.viatra.addon.viewers.runtime.specifications;

import org.eclipse.viatra.addon.viewers.runtime.notation.NotationPackage;
import org.eclipse.viatra.query.runtime.api.IQuerySpecification;
import org.eclipse.viatra.query.runtime.matchers.psystem.queries.PParameter;

import com.google.common.collect.Multimap;

class SpecificationDescriptorUtilities {

    private SpecificationDescriptorUtilities() {
        //Hiding utility class constructor
    }
    
    static void insertToTraces(IQuerySpecification<?> specification,
            Multimap<PParameter, PParameter> traces, String parameter) {
        String targetName = "trace<" + parameter + ">";
        PParameter var_target = new PParameter(targetName, 
                NotationPackage.eINSTANCE.getNsURI() + "||"
                + NotationPackage.eINSTANCE.getItem().getName());
        int positionOfParameter = specification.getPositionOfParameter(parameter);
        PParameter var_source = specification.getParameters().get(positionOfParameter);
        traces.put(var_target, var_source);
    }

}

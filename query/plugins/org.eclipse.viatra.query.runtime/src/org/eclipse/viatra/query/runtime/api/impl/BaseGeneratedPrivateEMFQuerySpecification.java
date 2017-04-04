/*******************************************************************************
 * Copyright (c) 2010-2017, stampie, IncQuery Labs Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   stampie - initial API and implementation
 *******************************************************************************/
package org.eclipse.viatra.query.runtime.api.impl;

import org.eclipse.viatra.query.runtime.api.GenericPatternMatch;
import org.eclipse.viatra.query.runtime.api.GenericPatternMatcher;
import org.eclipse.viatra.query.runtime.api.GenericQuerySpecification;
import org.eclipse.viatra.query.runtime.api.ViatraQueryEngine;
import org.eclipse.viatra.query.runtime.api.scope.QueryScope;
import org.eclipse.viatra.query.runtime.emf.EMFScope;
import org.eclipse.viatra.query.runtime.exception.ViatraQueryException;
import org.eclipse.viatra.query.runtime.matchers.psystem.queries.PQuery;

/**
 * 
 * Provides common functionality of pattern-specific generated query specifications for private patterns over the EMF
 * scope.
 * 
 * @since 1.6
 *
 */
public abstract class BaseGeneratedPrivateEMFQuerySpecification extends GenericQuerySpecification<GenericPatternMatcher> {

    public BaseGeneratedPrivateEMFQuerySpecification(PQuery wrappedPQuery) {
        super(wrappedPQuery);
    }

    @Override
    public Class<? extends QueryScope> getPreferredScopeClass() {
        return EMFScope.class;
    }

    @Override
    protected GenericPatternMatcher instantiate(final ViatraQueryEngine engine) throws ViatraQueryException {
      return defaultInstantiate(engine);
    }
    
    @Override
    public GenericPatternMatcher instantiate() throws ViatraQueryException {
      return new GenericPatternMatcher(this);
    }
    
    @Override
    public GenericPatternMatch newEmptyMatch() {
      return GenericPatternMatch.newEmptyMatch(this);
    }
    
    @Override
    public GenericPatternMatch newMatch(final Object... parameters) {
      return GenericPatternMatch.newMatch(this, parameters);
    }
}

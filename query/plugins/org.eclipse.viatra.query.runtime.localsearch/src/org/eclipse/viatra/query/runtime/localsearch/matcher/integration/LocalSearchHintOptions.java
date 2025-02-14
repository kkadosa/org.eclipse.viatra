/*******************************************************************************
 * Copyright (c) 2010-2016, Gabor Bergmann, IncQueryLabs Ltd.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-v20.html.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.viatra.query.runtime.localsearch.matcher.integration;

import org.eclipse.viatra.query.runtime.localsearch.planner.cost.ICostFunction;
import org.eclipse.viatra.query.runtime.localsearch.planner.cost.impl.IndexerBasedConstraintCostFunction;
import org.eclipse.viatra.query.runtime.matchers.backend.ICallDelegationStrategy;
import org.eclipse.viatra.query.runtime.matchers.backend.QueryHintOption;
import org.eclipse.viatra.query.runtime.matchers.psystem.rewriters.IFlattenCallPredicate;

/**
 * 
 * @author Gabor Bergmann
 * @since 1.5
 * @noinstantiate This class is not intended to be instantiated by clients.
 */
public final class LocalSearchHintOptions {
    
    private LocalSearchHintOptions() {
        // Private constructor for utility class
    }
    
    public static final QueryHintOption<Boolean> USE_BASE_INDEX = 
            hintOption("USE_BASE_INDEX", true);
    
    // This key can be used to influence the core planner algorithm
    public static final QueryHintOption<Integer> PLANNER_TABLE_ROW_COUNT = 
            hintOption("PLANNER_TABLE_ROW_COUNT", 4);
    /**
     * Cost function to be used by the planner. Must implement {@link ICostFunction}
     * @since 1.4
     */
    public static final QueryHintOption<ICostFunction> PLANNER_COST_FUNCTION = 
            hintOption("PLANNER_COST_FUNCTION", new IndexerBasedConstraintCostFunction());
    /**
     * Predicate to decide whether to flatten specific positive pattern calls {@link IFlattenCallPredicate}
     * @since 1.4
     */
    public static final QueryHintOption<IFlattenCallPredicate> FLATTEN_CALL_PREDICATE = 
            hintOption("FLATTEN_CALL_PREDICATE", new DontFlattenDisjunctive());
    /**
     * Strategy to decide how hints (most importantly, backend selection) propagate across pattern calls. 
     * Must implement {@link ICallDelegationStrategy}.
     * @since 2.1
     */
    public static final QueryHintOption<ICallDelegationStrategy> CALL_DELEGATION_STRATEGY = 
            hintOption("CALL_DELEGATION_STRATEGY", ICallDelegationStrategy.FULL_BACKEND_ADHESION);
    
    /**
     * A provider of expected adornments {@link IAdornmentProvider}. 
     * 
     * The safe default is {@link AllValidAdornments}; 
     * however, the generic backend variant may safely use {@link LazyPlanningAdornments} instead. 
     * 
     * @since 1.5
     */
    public static final QueryHintOption<IAdornmentProvider> ADORNMENT_PROVIDER = 
            hintOption("ADORNMENT_PROVIDER", new AllValidAdornments());
    
    /**
     * Whether the search-based query backend is to use registered surrogate queries instead of derived features.
     * 
     * <p>If set to false, the query evaluation will access derived features normally, and take their output at face value. 
     * Normally, this should be fine for the search-based backend (as opposed to caching backends), and come at a lower performance cost. 
     * Hence the default is false.  
     * 
     * <p> Setting this option to true will cause the backend to consult the surrogate query registered for a given feature, if any. 
     * Consider this if the default implementation of some features are known to be erroneous, 
     *  and there are surrogates queries that would provide correct results instead.
     * 
     * @since 2.9
     */
    public static final QueryHintOption<Boolean> CONSULT_SURROGATES = 
            hintOption("CONSULT_SURROGATES", false);
    

    
    // internal helper for conciseness
    private static <T, V extends T> QueryHintOption<T> hintOption(String hintKeyLocalName, V defaultValue) {
        return new QueryHintOption<>(LocalSearchHintOptions.class, hintKeyLocalName, defaultValue);
    }
}

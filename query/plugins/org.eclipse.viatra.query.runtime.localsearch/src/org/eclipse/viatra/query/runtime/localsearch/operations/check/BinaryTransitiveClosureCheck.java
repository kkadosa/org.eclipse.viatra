/*******************************************************************************
 * Copyright (c) 2010-2013, Zoltan Ujhelyi, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Zoltan Ujhelyi - initial API and implementation
 *******************************************************************************/
package org.eclipse.viatra.query.runtime.localsearch.operations.check;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import org.eclipse.viatra.query.runtime.localsearch.MatchingFrame;
import org.eclipse.viatra.query.runtime.localsearch.matcher.ISearchContext;
import org.eclipse.viatra.query.runtime.localsearch.operations.CheckOperationExecutor;
import org.eclipse.viatra.query.runtime.localsearch.operations.IPatternMatcherOperation;
import org.eclipse.viatra.query.runtime.localsearch.operations.ISearchOperation;
import org.eclipse.viatra.query.runtime.localsearch.operations.util.CallInformation;
import org.eclipse.viatra.query.runtime.matchers.backend.IQueryResultProvider;
import org.eclipse.viatra.query.runtime.matchers.tuple.Tuple;

/**
 * Checking for a transitive closure expressed as a local search pattern matcher. The matched pattern must have two
 * parameters of the same model type.
 * 
 * @author Zoltan Ujhelyi
 * @noextend This class is not intended to be subclassed by clients.
 * @noinstantiate This class is not intended to be instantiated by clients.
 * 
 */
public class BinaryTransitiveClosureCheck implements ISearchOperation, IPatternMatcherOperation {

    private class Executor extends CheckOperationExecutor {
        
        @Override
        public void onInitialize(MatchingFrame frame, ISearchContext context) {
            super.onInitialize(frame, context);
            matcher = context.getMatcher(information.getReference());
            // Note: second parameter is NOT bound during execution, but the first is
        }

        @Override
        protected boolean check(MatchingFrame frame, ISearchContext context) {
            if (checkReflexive(frame)) {
                return true;
            }
            
            Object targetValue = frame.get(targetPosition);
            Queue<Object> sourcesToEvaluate = new LinkedList<>();
            sourcesToEvaluate.add(frame.get(sourcePosition));
            Set<Object> sourceEvaluated = new HashSet<>();
            final Object[] mappedFrame = new Object[] {null, null};
            while (!sourcesToEvaluate.isEmpty()) {
                Object currentValue = sourcesToEvaluate.poll();
                sourceEvaluated.add(currentValue);
                mappedFrame[0] = currentValue;
                for (Tuple match : (Iterable<Tuple>) () -> matcher.getAllMatches(mappedFrame).iterator()) {
                    Object foundTarget = match.get(1);
                    if (targetValue.equals(foundTarget)) {
                        return true;
                    } else if (!sourceEvaluated.contains(foundTarget)) {
                        sourcesToEvaluate.add(foundTarget);
                    }
                }
            }
            return false;
        }
        
        protected boolean checkReflexive(MatchingFrame frame) {
            return reflexive && Objects.equals(frame.get(sourcePosition), frame.get(targetPosition));
        }
        
        @Override
        public ISearchOperation getOperation() {
            return BinaryTransitiveClosureCheck.this;
        }
    }
    
    private final CallInformation information; 
    private IQueryResultProvider matcher;
    private final int sourcePosition;
    private final int targetPosition;
    private final boolean reflexive;
    
    /**
     * The source position will be matched in the called pattern to the first parameter; while target to the second.
     * </p>
     * <strong>NOTE</strong>: the reflexive check call does not include the parameter type checks; appropriate type checks should be
     * added as necessary by the operation compiler.
     * 
     * @since 2.0
     */
    public BinaryTransitiveClosureCheck(CallInformation information, int sourcePosition, int targetPosition, boolean reflexive) {
        super();
        this.sourcePosition = sourcePosition;
        this.targetPosition = targetPosition;
        this.information = information;
        this.reflexive = reflexive;
    }
    
    @Override
    public ISearchOperationExecutor createExecutor() {
        return new Executor();
    }

    @Override
    public String toString() {
        String c = information.toString();
        int p = c.indexOf('(');
        return "check     find "+c.substring(0, p)+"+"+c.substring(p);
    }

    @Override
    public List<Integer> getVariablePositions() {
        return Arrays.asList(sourcePosition, targetPosition);
    }

}

/*******************************************************************************
 * Copyright (c) 2010-2015, Marton Bur, Zoltan Ujhelyi, Akos Horvath, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Marton Bur, Zoltan Ujhelyi, Akos Horvath - initial API and implementation
 *******************************************************************************/
package org.eclipse.viatra.query.runtime.localsearch.operations.extend.nobase;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.viatra.query.runtime.base.api.NavigationHelper;
import org.eclipse.viatra.query.runtime.emf.EMFScope;
import org.eclipse.viatra.query.runtime.emf.types.EDataTypeInSlotsKey;
import org.eclipse.viatra.query.runtime.localsearch.MatchingFrame;
import org.eclipse.viatra.query.runtime.localsearch.matcher.ISearchContext;
import org.eclipse.viatra.query.runtime.localsearch.operations.IIteratingSearchOperation;
import org.eclipse.viatra.query.runtime.localsearch.operations.ISearchOperation;
import org.eclipse.viatra.query.runtime.matchers.context.IInputKey;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Iterates over all {@link EDataType} instances without using an {@link NavigationHelper VIATRA Base indexer}.
 * 
 */
public class IterateOverEDatatypeInstances implements IIteratingSearchOperation {

    private class Executor extends AbstractIteratingExtendOperationExecutor<Object> {
        
        public Executor(int position, EMFScope scope) {
            super(position, scope);
        }

        @Override
        public Iterator<? extends Object> getIterator(MatchingFrame frame, final ISearchContext context) {
            return getModelContents().filter(EObject.class::isInstance).map(EObject.class::cast)
                    .map(input -> doGetEAttributes(input.eClass(), context)
                            .map(attribute -> {
                                if (attribute.isMany()) {
                                    return ((List<?>) input.eGet(attribute)).stream();
                                } else {
                                    Object o = input.eGet(attribute);
                                    return o == null ? Stream.empty() : Stream.of(o);
                                }
                            }))
                    .flatMap(i -> i)
                    .<Object>flatMap(i -> i)
                    .iterator();
        }
        
        @Override
        public ISearchOperation getOperation() {
            return IterateOverEDatatypeInstances.this;
        }
    }
    
    private final EDataType dataType;
    private final int position;
    private final EMFScope scope;
    
    public IterateOverEDatatypeInstances(int position, EDataType dataType, EMFScope scope) {
        this.position = position;
        this.dataType = dataType;
        this.scope = scope;
    }
    
    protected Stream<EAttribute> doGetEAttributes(EClass eclass, ISearchContext context){
        @SuppressWarnings({ "unchecked"})
        Table<EDataType, EClass, Set<EAttribute>> cache = context.accessBackendLevelCache(getClass(), Table.class, HashBasedTable::create);
        if(!cache.contains(dataType, eclass)) {
            EList<EAttribute> eAllAttributes = eclass.getEAllAttributes();
            cache.put(dataType, eclass, 
                    eAllAttributes.stream().filter(input -> Objects.equals(input.getEType(), dataType)).collect(Collectors.toSet())
            );
        }
        return cache.get(dataType, eclass).stream();
    }

    public EDataType getDataType() {
        return dataType;
    }
    
    @Override
    public ISearchOperationExecutor createExecutor() {
        return new Executor(position, scope);
    }
    
    @Override
    public String toString() {
        return toString(Object::toString);
    }
    
    @Override
    public String toString(Function<Integer, String> variableMapping) {
        return "extend    "+dataType.getName()+"(-"+variableMapping.apply(position)+") iterating";
    }
    
    @Override
    public List<Integer> getVariablePositions() {
        return Collections.singletonList(position);
    }
    
    /**
     * @since 1.4
     */
    @Override
    public IInputKey getIteratedInputKey() {
        return new EDataTypeInSlotsKey(dataType);
    }

}

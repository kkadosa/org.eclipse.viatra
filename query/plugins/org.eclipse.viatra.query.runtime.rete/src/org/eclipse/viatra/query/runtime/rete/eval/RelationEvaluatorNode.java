/*******************************************************************************
 * Copyright (c) 2010-2022, Tamas Szabo, GitHub
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-v20.html.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.viatra.query.runtime.rete.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.IntStream;

import org.eclipse.viatra.query.runtime.matchers.psystem.IRelationEvaluator;
import org.eclipse.viatra.query.runtime.matchers.psystem.basicdeferred.RelationEvaluation;
import org.eclipse.viatra.query.runtime.matchers.tuple.Tuple;
import org.eclipse.viatra.query.runtime.matchers.util.Clearable;
import org.eclipse.viatra.query.runtime.matchers.util.Direction;
import org.eclipse.viatra.query.runtime.matchers.util.timeline.Timeline;
import org.eclipse.viatra.query.runtime.matchers.util.timeline.Timelines;
import org.eclipse.viatra.query.runtime.rete.misc.SimpleReceiver;
import org.eclipse.viatra.query.runtime.rete.network.ProductionNode;
import org.eclipse.viatra.query.runtime.rete.network.Receiver;
import org.eclipse.viatra.query.runtime.rete.network.ReteContainer;
import org.eclipse.viatra.query.runtime.rete.network.StandardNode;
import org.eclipse.viatra.query.runtime.rete.network.Supplier;
import org.eclipse.viatra.query.runtime.rete.network.communication.CommunicationTracker;
import org.eclipse.viatra.query.runtime.rete.network.communication.Timestamp;
import org.eclipse.viatra.query.runtime.rete.single.AbstractUniquenessEnforcerNode;

/**
 * A node that operates in batch-style (see {@link Receiver#doesProcessUpdatesInBatch()} and evaluates arbitrary Java
 * logic represented by an {@link IRelationEvaluator} on the input relations. This is the backing computation node of a
 * {@link RelationEvaluation} constraint.
 * 
 * @author Tamas Szabo
 * @since 2.8
 */
public class RelationEvaluatorNode extends StandardNode implements Supplier, Clearable {

    private final IRelationEvaluator evaluator;
    private Set<Tuple> cachedOutputs;
    private Supplier[] inputSuppliers;
    private BatchingReceiver[] inputReceivers;

    public RelationEvaluatorNode(final ReteContainer container, final IRelationEvaluator evaluator,
            final BatchingReceiver[] inputReceivers) {
        super(container);
        this.evaluator = evaluator;
        this.inputReceivers = inputReceivers;
        for (final BatchingReceiver inputReceiver : inputReceivers) {
            inputReceiver.setContainerNode(this);
        }
        this.reteContainer.registerClearable(this);
    }

    public RelationEvaluatorNode(final ReteContainer container, final IRelationEvaluator evaluator) {
        this(container, evaluator, IntStream.range(0, evaluator.getInputArities().size())
                .mapToObj(i -> new BatchingReceiver(container)).toArray(BatchingReceiver[]::new));
    }

    @Override
    public void clear() {
        this.cachedOutputs.clear();
    }

    public void connectToParents(final List<Supplier> inputSuppliers) {
        this.inputSuppliers = new Supplier[inputSuppliers.size()];

        final List<Integer> inputArities = evaluator.getInputArities();

        if (inputArities.size() != inputSuppliers.size()) {
            throw new IllegalStateException(evaluator.toString() + " expects " + inputArities.size()
                    + " inputs, but got " + inputSuppliers.size() + " input(s)!");
        }

        for (int i = 0; i < inputSuppliers.size(); i++) {
            final int currentExpectedInputArity = inputArities.get(i);
            final Supplier inputSupplier = inputSuppliers.get(i);
            // it is expected that the supplier is a production node because
            // the corresponding constraint itself accepts a list of PQuery
            if (!(inputSupplier instanceof ProductionNode)) {
                throw new IllegalStateException(
                        evaluator.toString() + " expects each one of its suppliers to be instances of "
                                + ProductionNode.class.getSimpleName() + " but got an instance of "
                                + inputSupplier.getClass().getSimpleName() + "!");
            }
            final int currentActualInputArity = ((ProductionNode) inputSupplier).getPosMapping().size();
            if (currentActualInputArity != currentExpectedInputArity) {
                throw new IllegalStateException(
                        evaluator.toString() + " expects input arity " + currentExpectedInputArity + " at position " + i
                                + " but got " + currentActualInputArity + "!");
            }
            final BatchingReceiver inputReceiver = this.inputReceivers[i];
            inputReceiver.setSourceNode((ProductionNode) inputSupplier);

            this.inputSuppliers[i] = inputSupplier;
            this.reteContainer.connectAndSynchronize(inputSupplier, inputReceiver);
            reteContainer.getCommunicationTracker().registerDependency(inputReceiver, this);
        }

        // initialize the output relation
        final List<Set<Tuple>> inputSets = new ArrayList<Set<Tuple>>();
        for (final BatchingReceiver inputReceiver : this.inputReceivers) {
            inputSets.add(inputReceiver.getTuples());
        }
        this.cachedOutputs = evaluateRelation(inputSets);
    }

    @Override
    public void networkStructureChanged() {
        if (this.reteContainer.getCommunicationTracker().isInRecursiveGroup(this)) {
            throw new IllegalStateException(this.toString() + " cannot be used in recursive evaluation!");
        }
        super.networkStructureChanged();
    }

    @Override
    public void pullInto(final Collection<Tuple> collector, final boolean flush) {
        collector.addAll(this.cachedOutputs);
    }

    @Override
    public void pullIntoWithTimeline(final Map<Tuple, Timeline<Timestamp>> collector, final boolean flush) {
        final Timeline<Timestamp> timeline = Timelines.createFrom(Timestamp.ZERO);
        for (final Tuple output : this.cachedOutputs) {
            collector.put(output, timeline);
        }
    }

    private Set<Tuple> evaluateRelation(final List<Set<Tuple>> inputs) {
        try {
            return this.evaluator.evaluateRelation(inputs);
        } catch (final Exception e) {
            throw new IllegalStateException("Exception during the evaluation of " + this.evaluator.toString() + "!", e);
        }
    }

    private void batchUpdateCompleted() {
        final List<Set<Tuple>> inputSets = new ArrayList<Set<Tuple>>();
        for (final BatchingReceiver inputReceiver : this.inputReceivers) {
            inputSets.add(inputReceiver.getTuples());
        }
        final Set<Tuple> newOutputs = evaluateRelation(inputSets);
        for (final Tuple tuple : newOutputs) {
            if (this.cachedOutputs != null && this.cachedOutputs.remove(tuple)) {
                // already known tuple - do nothing
            } else {
                // newly inserted tuple
                propagateUpdate(Direction.INSERT, tuple, Timestamp.ZERO);
            }
        }
        if (this.cachedOutputs != null) {
            for (final Tuple tuple : this.cachedOutputs) {
                // lost tuple
                propagateUpdate(Direction.DELETE, tuple, Timestamp.ZERO);
            }
        }
        this.cachedOutputs = newOutputs;
    }

    public static class BatchingReceiver extends SimpleReceiver {
        private ProductionNode sourceNode;
        private RelationEvaluatorNode containerNode;

        private BatchingReceiver(final ReteContainer container) {
            super(container);
        }

        public void setSourceNode(final ProductionNode sourceNode) {
            this.sourceNode = sourceNode;
        }

        public ProductionNode getSourceNode() {
            return this.sourceNode;
        }

        public void setContainerNode(final RelationEvaluatorNode containerNode) {
            this.containerNode = containerNode;
        }

        public RelationEvaluatorNode getContainerNode() {
            return this.containerNode;
        }

        private Set<Tuple> getTuples() {
            return ((AbstractUniquenessEnforcerNode) this.sourceNode).getTuples();
        }

        @Override
        public void update(final Direction direction, final Tuple updateElement, final Timestamp timestamp) {
            throw new UnsupportedOperationException("This receiver only supports batch-style operation!");
        }

        @Override
        public void batchUpdate(final Collection<Entry<Tuple, Integer>> updates, final Timestamp timestamp) {
            assert Timestamp.ZERO.equals(timestamp);
            // The source production node has already updated itself, so there is no need to do anything with the input
            // updates. We will just use the tuples maintained in the memory of the production node.
            // However, we should guard against spurious calls to the evaluation logic as much as possible, and only
            // really issue the call if this is the "last" batchUpdate call among all the batching receivers of this
            // relation evaluator node. It can happen that certain batching receivers are "lacking behind" because
            // their ancestor may not have processed their updates yet. In such cases, the batchUpdateCompleted will
            // be called potentially unnecessarily again, which is an issue for performance but not an issue for
            // correctness.
            final CommunicationTracker tracker = this.containerNode.getCommunicationTracker();
            if (Arrays.stream(this.containerNode.inputReceivers).noneMatch(receiver -> {
                return tracker.isEnqueued(receiver);
            })) {
                this.containerNode.batchUpdateCompleted();
            }
        }

        @Override
        protected String getTraceInfoPatternsEnumerated() {
            return this.getContainerNode().toString();
        }

    }

}

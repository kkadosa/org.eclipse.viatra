/*******************************************************************************
 * Copyright (c) 2010-2016, Andras Szabolcs Nagy, Zoltan Ujhelyi and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 *   Andras Szabolcs Nagy - initial API and implementation
 *******************************************************************************/
package org.eclipse.viatra.dse.solutionstore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.eclipse.viatra.dse.api.DSEException;
import org.eclipse.viatra.dse.api.Solution;
import org.eclipse.viatra.dse.api.SolutionTrajectory;
import org.eclipse.viatra.dse.base.DesignSpaceManager;
import org.eclipse.viatra.dse.base.ThreadContext;
import org.eclipse.viatra.dse.objectives.Fitness;
import org.eclipse.viatra.dse.objectives.ObjectiveComparatorHelper;
import org.eclipse.viatra.dse.statecode.IStateCoderFactory;

/**
 * 
 * @author Andras Szabolcs Nagy
 *
 */
public class SolutionStore {

    public interface ISolutionSaver {
        void setSolutionsCollection(Map<Object, Solution> solutions);
        boolean saveSolution(ThreadContext context, Object id, SolutionTrajectory solutionTrajectory);
    }

    public interface IEnoughSolutions extends ISolutionFoundHandler {
        boolean enoughSolutions();
    }

    public interface ISolutionFoundHandler {
        void solutionFound(ThreadContext context, SolutionTrajectory trajectory);
        void solutionTriedToSave(ThreadContext context, SolutionTrajectory trajectory);
    }

    public static class ANumberOfEnoughSolutions implements IEnoughSolutions {

        private final AtomicInteger foundSolutions;
        private final AtomicBoolean foundEnoughSolutions;

        public ANumberOfEnoughSolutions(int number) {
            foundSolutions = new AtomicInteger(number);
            foundEnoughSolutions = new AtomicBoolean(false);
        }

        @Override
        public boolean enoughSolutions() {
            return foundEnoughSolutions.get();
        }

        @Override
        public void solutionFound(ThreadContext context, SolutionTrajectory trajectory) {
            int solutionsToFind = foundSolutions.decrementAndGet();
            if (solutionsToFind == 0) {
                foundEnoughSolutions.set(true);
            }
        }

        @Override
        public void solutionTriedToSave(ThreadContext context, SolutionTrajectory trajectory) {
        }
    }

    public static class LogSolutionHandler implements ISolutionFoundHandler {

        Logger logger = Logger.getLogger(LogSolutionHandler.class);

        @Override
        public void solutionFound(ThreadContext context, SolutionTrajectory trajectory) {
            logger.info("Solution registered: " + trajectory.toPrettyString());
        }

        @Override
        public void solutionTriedToSave(ThreadContext context, SolutionTrajectory trajectory) {
            logger.debug("Not good enough solution: " + trajectory.toPrettyString());
        }
    }

    public static class SimpleSolutionSaver implements ISolutionSaver {

        private Map<Object, Solution> solutions;

        @Override
        public void setSolutionsCollection(Map<Object, Solution> solutions) {
            this.solutions = solutions;
        }

        @Override
        public boolean saveSolution(ThreadContext context, Object id, SolutionTrajectory solutionTrajectory) {
            Solution solution = solutions.get(id);
            if (solution != null) {
                if (solution.getTrajectories().contains(solutionTrajectory)) {
                    return false;
                } else {
                    solution.addTrajectory(solutionTrajectory);
                    solutionTrajectory.setSolution(solution);
                }
            } else {
                solution = new Solution(id, solutionTrajectory);
                solutions.put(id, solution);
                solutionTrajectory.setSolution(solution);
            }
            return true;
        }
    }

    public static class BestSolutionSaver implements ISolutionSaver {

        private Map<Object, Solution> solutions;
        private Map<SolutionTrajectory, Fitness> trajectories = new HashMap<>();

        @Override
        public void setSolutionsCollection(Map<Object, Solution> solutions) {
            this.solutions = solutions;
        }

        @Override
        public boolean saveSolution(ThreadContext context, Object id, SolutionTrajectory solutionTrajectory) {

            Fitness lastFitness = context.getLastFitness();
            ObjectiveComparatorHelper comparatorHelper = context.getObjectiveComparatorHelper();

            List<SolutionTrajectory> dominatedTrajectories = new ArrayList<>();

            for (Entry<SolutionTrajectory, Fitness> entry : trajectories.entrySet()) {
                int isLastFitnessBetter = comparatorHelper.compare(lastFitness, entry.getValue());
                if (isLastFitnessBetter < 0) {
                    return false;
                }
                if (isLastFitnessBetter > 0) {
                    dominatedTrajectories.add(entry.getKey());
                }
            }

            boolean solutionSaved = false;

            Solution solution = solutions.get(id);
            if (solution != null) {
                if (!solution.getTrajectories().contains(solutionTrajectory)) {
                    solution.addTrajectory(solutionTrajectory);
                    solutionTrajectory.setSolution(solution);
                    solutionSaved = true;
                    trajectories.put(solutionTrajectory, lastFitness);
                }
            } else {
                solution = new Solution(id, solutionTrajectory);
                solutions.put(id, solution);
                solutionTrajectory.setSolution(solution);
                solutionSaved = true;
                trajectories.put(solutionTrajectory, lastFitness);
            }

            for (SolutionTrajectory st : dominatedTrajectories) {
                trajectories.remove(st);
                Solution s = st.getSolution();
                if (!s.getTrajectories().remove(st)) {
                    throw new DSEException("Should not happen.");
                }
                if (s.getTrajectories().isEmpty()) {
                    Object stateCode = s.getStateCode();
                    solutions.remove(stateCode);
                }
            }

            return solutionSaved;
        }

    }

    protected boolean acceptOnlyGoalSolutions = true;
    protected final Map<Object, Solution> solutions = new HashMap<Object, Solution>();
    protected ISolutionSaver solutionSaver = new SimpleSolutionSaver();
    protected List<ISolutionFoundHandler> solutionFoundHandlers = new ArrayList<ISolutionFoundHandler>(1);

    protected final IEnoughSolutions enoughSolutions;

    public SolutionStore() {
        this(new IEnoughSolutions() {
            @Override
            public void solutionFound(ThreadContext context, SolutionTrajectory trajectory) {
            }

            @Override
            public boolean enoughSolutions() {
                return false;
            }

            @Override
            public void solutionTriedToSave(ThreadContext context, SolutionTrajectory trajectory) {
            }
        });
    }

    public SolutionStore(int numOfSolutionsToFind) {
        this(new ANumberOfEnoughSolutions(numOfSolutionsToFind));
    }

    public SolutionStore(IEnoughSolutions enoughSolutionsImpl) {
        enoughSolutions = enoughSolutionsImpl;
    }

    public synchronized void newSolution(ThreadContext context) {
        solutionSaver.setSolutionsCollection(solutions);
        Fitness fitness = context.getLastFitness();
        DesignSpaceManager dsm = context.getDesignSpaceManager();
        Object id = dsm.getCurrentState();
        IStateCoderFactory stateCoderFactory = context.getGlobalContext().getStateCoderFactory();
        SolutionTrajectory solutionTrajectory = dsm.getTrajectoryInfo().createSolutionTrajectory(stateCoderFactory);
        solutionTrajectory.setFitness(fitness);

        if (acceptOnlyGoalSolutions && !fitness.isSatisifiesHardObjectives()) {
            unsavedSolutionCallbacks(context, solutionTrajectory);
            return;
        }

        boolean solutionSaved = solutionSaver.saveSolution(context, id, solutionTrajectory);

        if (solutionSaved) {
            enoughSolutions.solutionFound(context, solutionTrajectory);

            savedSolutionCallbacks(context, solutionTrajectory);

            if (enoughSolutions.enoughSolutions()) {
                context.getGlobalContext().stopAllThreads();
            }
        } else {
            unsavedSolutionCallbacks(context, solutionTrajectory);
        }
    }

    private void unsavedSolutionCallbacks(ThreadContext context, SolutionTrajectory solutionTrajectory) {
        for (ISolutionFoundHandler handler : solutionFoundHandlers) {
            handler.solutionFound(context, solutionTrajectory);
        }
    }

    private void savedSolutionCallbacks(ThreadContext context, SolutionTrajectory solutionTrajectory) {
        for (ISolutionFoundHandler handler : solutionFoundHandlers) {
            handler.solutionTriedToSave(context, solutionTrajectory);
        }
    }

    public synchronized Collection<Solution> getSolutions() {
        return solutions.values();
    }

    public synchronized void registerSolutionFoundHandler(ISolutionFoundHandler handler) {
        if (solutionFoundHandlers == null) {
            solutionFoundHandlers = new ArrayList<ISolutionFoundHandler>(1);
        }
        solutionFoundHandlers.add(handler);
    }

    public SolutionStore logSolutionsWhenFound() {
        registerSolutionFoundHandler(new LogSolutionHandler());
        Logger.getLogger(LogSolutionHandler.class).setLevel(Level.INFO);
        return this;
    }

    public SolutionStore acceptGoalSolutionsOnly() {
        acceptOnlyGoalSolutions = true;
        return this;
    }

    public SolutionStore acceptAnySolutions() {
        acceptOnlyGoalSolutions = false;
        return this;
    }

    public SolutionStore withSolutionSaver(ISolutionSaver solutionSaver) {
        this.solutionSaver = solutionSaver;
        return this;
    }

    public SolutionStore storeBestSolutionsOnly() {
        this.solutionSaver = new BestSolutionSaver();
        return this;
    }
}

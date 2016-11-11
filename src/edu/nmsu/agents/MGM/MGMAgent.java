package edu.nmsu.agents.MGM;

import edu.nmsu.communication.BasicMessage;
import edu.nmsu.communication.ComAgent;
import edu.nmsu.communication.DCOPagent;

import java.util.List;

/**
 * Created by nandofioretto on 11/1/16.
 */
public class MGMAgent extends DCOPagent {

    private int nbCycles;
    private int currentCycle;

    private MGMAgentView view;
    private MGMAgentActions actions;

    /**
     * @param statsCollector
     * @param agentState
     * @param parameters 1. Number of MGM Cycles
     *                   2. Timeout for CP-solver
     *                   3. weight for first objective (w_cost)
     *                   4. weight for second objective (w_power)
     */
    public MGMAgent(ComAgent statsCollector, MGMAgentState agentState, List<Object> parameters) {
            super(statsCollector, agentState.getName(), agentState.getID());

        nbCycles = (int)parameters.get(0);
        long solver_timout = (long)parameters.get(1);
        double w_price  = (double)parameters.get(2);
        double w_power = (double)parameters.get(3);

        view = new MGMAgentView(agentState);
        actions = new MGMAgentActions(agentState, solver_timout, w_price, w_power);

        currentCycle = 0;
    }

    @Override
    protected void onReceive(Object message, ComAgent sender) {
        super.onReceive(message, sender);

        if (message instanceof GainMessage) {
            GainMessage msg = (GainMessage) message;
            actions.storeMessage(sender.getId(), currentCycle, msg);
        }
    }


    @Override
    protected boolean terminationCondition() {
        return currentCycle >= nbCycles;
    }

    @Override
    protected void onStart() {
        System.out.println(getName() + " computing FIRST schedule");

        actions.computeFirstSchedule();

        // Send Gain to neighbors
        actions.computeGain();
        GainMessage gainMessage =
                new GainMessage(currentCycle, view.getGain(), view.getCurrentSchedule().getPowerConsumptionKw());
        for (ComAgent neighbor : this.getNeighborsRef() ) {
            this.tell(gainMessage, neighbor);
        }

        while (!terminationCondition()) {
            System.out.println(getName() + " cycle" + currentCycle + " cost: " + view.getCurrentSchedule().getCost());
            MGMcycle();

            getAgentStatistics().updateIterationStats(view.getBestSchedule(),
                    view.getSolvingTimeMs(),
                    view.getGain());

            currentCycle++;
        }
    }

    @Override
    protected void onStop() {
        //System.out.println(view.getBestSchedule().toString());
    }

    public void MGMcycle() {

        while (!actions.receivedAllGains(currentCycle)) {
            System.out.println(getName() + " waiting");
            await();
        }

        // Check if this agent has the best gain: if so   bestSchedule = currentSchedule;
        if (actions.isBestGain(currentCycle)) {
            actions.updateBestSchedule(view.getCurrentSchedule());
        }

        // Reset gain data structure
        //actions.clearReceivedGains();

        System.out.println(getName() + " computing schedule");
        // Sums the agent loads and computes the new schedule
        if (actions.computeSchedule(currentCycle))
        {
            actions.computeGain();

            // Send Gain message to all neighbors
            GainMessage gainMessage =
                    new GainMessage(currentCycle+1, view.getGain(), view.getCurrentSchedule().getPowerConsumptionKw());
            for (ComAgent neighbor : this.getNeighborsRef() ) {
                this.tell(gainMessage, neighbor);
            }

        }
    }


    /////////////////////////////////////////////////////////////////////////
    // Messages
    /////////////////////////////////////////////////////////////////////////
    public static class GainMessage extends BasicMessage {
        private final int cycle;
        private final double gain;
        private final Double[] energyProfile;

        public GainMessage(int cycle, double gain, Double[] energyProfile) {
            this.cycle = cycle;
            this.gain = gain;
            this.energyProfile = energyProfile;
        }

        public int getCycle() {
            return cycle;
        }

        public double getGain() {
            return gain;
        }

        public Double[] getEnergyProfile() {
            return energyProfile;
        }

        @Override
        public String toString() {
            return "MGM::GainMessage";
        }
    }

}

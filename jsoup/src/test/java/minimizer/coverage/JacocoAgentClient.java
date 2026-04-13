package minimizer.coverage;

import org.jacoco.agent.rt.IAgent;
import org.jacoco.agent.rt.RT;

public final class JacocoAgentClient {
    private final IAgent agent;

    private JacocoAgentClient(IAgent agent) {
        this.agent = agent;
    }

    public static boolean isAgentActive() {
        try {
            RT.getAgent();
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    static JacocoAgentClient connect() {
        try {
            return new JacocoAgentClient(RT.getAgent());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("JaCoCo agent is not active. Run with -javaagent:<path-to-org.jacoco.agent-*-runtime.jar>=output=none,dumponexit=false", ex);
        }
    }

    void reset() {
        agent.reset();
    }

    byte[] dumpExecutionData(boolean reset) {
        return agent.getExecutionData(reset);
    }
}

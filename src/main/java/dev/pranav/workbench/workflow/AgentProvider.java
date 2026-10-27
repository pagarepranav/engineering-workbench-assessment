package dev.pranav.workbench.workflow;

/** No paid API dependency. Prompt export and structured response import are explicit. */
public interface AgentProvider {
    String prompt(Workflow.Run run, Workflow.Stage stage);
}

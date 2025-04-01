package com.diakonie.network_flow.controller;

import com.diakonie.network_flow.model.Task;
import com.diakonie.network_flow.service.CpmService;
import com.diakonie.network_flow.service.ProjectDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectDataService projectDataService;
    private final CpmService cpmService;

    /**
     * Constructor injection is used for dependencies (ProjectDataService, CpmService).
     * This is the preferred way to inject dependencies in Spring Boot.
     * @param projectDataService Service for loading task data.
     * @param cpmService Service for performing CPM calculations.
     */
    public ProjectController(ProjectDataService projectDataService, CpmService cpmService) {
        this.projectDataService = projectDataService;
        this.cpmService = cpmService;
    }

    /**
     * Handles GET requests to the root path ("/") of the web application.
     * Orchestrates loading data, running calculations, and preparing the model for the view.
     *
     * @param model The Spring Model object, used to pass data to the Thymeleaf template.
     * @return The logical name of the Thymeleaf view template ("network-view") to be rendered.
     */
    @GetMapping("/")
    public String showNetworkDiagram(Model model) {
        log.info("Received request to show network diagram.");

        String dataFilePath = "data/tasks.json";

        try {
            // First: Loads raw task data
            List<Task> tasks = projectDataService.loadTasksFromJson(dataFilePath);

            if (tasks.isEmpty()) {
                model.addAttribute("error", "Vorgangsdaten konnten nicht aus " + dataFilePath + " geladen werden.");
            } else {
                // Second: Performs calculations
                List<Task> calculatedTasks = cpmService.performCpmCalculations(tasks);

                // Third: Prepares data for the view
                // Adds the list of tasks (with calculated values) to the model
                // The key "tasks" will be used in the Thymeleaf template to access this list
                model.addAttribute("tasks", calculatedTasks);

                // Generates the text-based syntax required by Mermaid.js (and adds it to the model)
                String mermaidSyntax = generateMermaidSyntax(calculatedTasks);
                model.addAttribute("mermaidSyntax", mermaidSyntax);

                log.info("Added {} calculated tasks and Mermaid syntax to the model.", calculatedTasks.size());
            }
        } catch (Exception e) {
            log.error("An error occurred processing the network diagram request.", e);
            model.addAttribute("error", "Ein unerwarteter Fehler ist aufgetreten: " + e.getMessage());
        }

        // Return the name of the template file (without extension) located in
        // src/main/resources/templates/
        return "network-view";
    }

    /**
     * Generates the graph definition syntax for Mermaid.js based on the calculated tasks.
     * Mermaid expects a specific text format to define nodes, links, and styles.
     *
     * @param tasks List of tasks with CPM calculations done.
     * @return A String containing the Mermaid graph definition (for example: "graph TD; A[...]; B[...]; A --> B;").
     */
    private String generateMermaidSyntax(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("graph TD;\n"); // TD = Top Down graph layout

        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            // Formats node label with multiple lines using HTML <br> (line break)
            String nodeLabel = String.format("%s[\"%s<br>--------------------<br>FAZ:%d  FEZ:%d<br>SAZ:%d  SEZ:%d<br>--------------------<br>Dauer:%d | GP:%d | FP:%d\"]",
                    task.getId(),
                    escapeMermaidLabel(task.getDescription()),
                    // Corner Times
                    task.getEarliestStartDate(),   // FAZ
                    task.getEarliestFinishDate(),  // FEZ
                    task.getLatestStartDate(),     // SAZ
                    task.getLatestFinishDate(),    // SEZ
                    // Bottom Row
                    task.getDuration(),            // Dauer
                    task.getTotalFloat(),          // GP
                    task.getFreeFloat());          // FP
            sb.append("    ").append(nodeLabel).append(";\n");

            if (task.isCritical()) {
                // Applies specific CSS styling to critical nodes
                sb.append("    style ").append(task.getId()).append(" fill:#f9f,stroke:#333,stroke-width:2px,color:#fff;\n");
            }
        }
        sb.append("\n");

        // Defines links (edges) and applies critical link styling
        // It is necessary to track link index for applying `linkStyle` correctly!
        StringBuilder links = new StringBuilder();
        StringBuilder linkStyles = new StringBuilder();
        int linkIndex = 0;

        for (Task task : tasks) {
            if (task.getPredecessorIds() != null && !task.getPredecessorIds().isEmpty()) {
                 // Finds the actual predecessor tasks to check if the link is critical
                List<Task> predecessors = task.getPredecessorIds().stream()
                    .map(predId -> tasks.stream().filter(t -> t.getId().equals(predId)).findFirst().orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());

                for (Task predecessor : predecessors) {
                     // A link is critical if both predecessor and successor tasks are critical AND Total Float is 0 for both
                     // (Checking TF helps distinguish truly critical path segments)
                    boolean isCriticalLink = predecessor.isCritical() && task.isCritical() && predecessor.getTotalFloat() == 0 && task.getTotalFloat() == 0;

                    // Defines link: Predecessor --> Successor (without duration label)
                    links.append("    ").append(predecessor.getId()).append(" --> ").append(task.getId()).append(";\n");

                    if (isCriticalLink) {
                        // Applies specific CSS styling to critical links by their definition order (index)
                        linkStyles.append("    linkStyle ").append(linkIndex).append(" stroke:#ff0000,stroke-width:4px;\n");
                    }
                    linkIndex++;
                }
            }
        }
        sb.append(links);
        sb.append(linkStyles);

        log.debug("Generated Mermaid Syntax:\n{}", sb.toString());
        return sb.toString();
    }

    /**
     * Escapes characters in a label that might interfere with Mermaid syntax.
     * Basic escaping for quotes.
     * @param label The raw label string.
     * @return The escaped label string.
     */
    private String escapeMermaidLabel(String label) {
        if (label == null) return "";
        // Replaces quotes that might break the ["..."] syntax
        // Using HTML entity #quot; for quotes inside the label string
        label = label.replace("\"", "#quot;");

        return label;
    }
}
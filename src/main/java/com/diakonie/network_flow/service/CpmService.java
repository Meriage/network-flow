package com.diakonie.network_flow.service;

import com.diakonie.network_flow.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CpmService {

    private static final Logger log = LoggerFactory.getLogger(CpmService.class);

    /**
     * Main public method to perform all CPM calculations (Forward Pass, Backward Pass, Floats).
     * This method orchestrates the entire calculation process.
     *
     * @param tasks The list of tasks loaded from the input source.
     * @return The same list of tasks, but with calculated CPM fields populated.
     */
    public List<Task> performCpmCalculations(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            log.warn("Task list is empty or null! Skipping CPM calculations.");

            return Collections.emptyList(); //? Is it better to return the empty list passed in?
        }

        log.info("Starting CPM calculations for {} tasks.", tasks.size());

        // --- Data Structure Setup ---
        //* Creating this map takes O(n) average time (n=number of tasks) as it iterates through the list once.
        //* However, subsequent lookups by ID using taskMap.get(id) are O(1) on average,
        //* which avoids repeatedly iterating through the list (O(n)) later.
        Map<String, Task> taskMap = tasks.stream()
                .collect(Collectors.toMap(Task::getId, task -> task));

        // Creates a map of successors (adjacency list representation).
        // Useful for finding tasks that depend on a given task during calculations (forward pass, free float).
        Map<String, List<Task>> successorMap = buildSuccessorMap(tasks, taskMap);

        // --- Calculation Steps ---
        //* The order is important: Forward pass calculates earliest times, which are needed
        //* as input for the backward pass (to determine project duration).
        //* Both passes are needed before floats can be calculated.
        calculateForwardPass(tasks, taskMap, successorMap);
        calculateBackwardPass(tasks, taskMap, successorMap);
        calculateFloatsAndCriticalPath(tasks, taskMap, successorMap);
        log.info("CPM calculations completed.");

        return tasks;
    }

    /**
     * Helper method to build a map linking each task ID to its direct successors.
     */
    private Map<String, List<Task>> buildSuccessorMap(List<Task> tasks, Map<String, Task> taskMap) {
        Map<String, List<Task>> successorMap = new HashMap<>();
        // Initializes map with empty lists for all tasks, for handling tasks with no successors
        tasks.forEach(task -> successorMap.put(task.getId(), new ArrayList<>()));

        // Populates the successor lists
        for (Task task : tasks) {
            if (task.getPredecessorIds() != null) {
                for (String predId : task.getPredecessorIds()) {
                    // Ensures the predecessor exists in the map before trying to add successor
                    if (taskMap.containsKey(predId)) {
                        // Gets the list for the predecessor and adds the current task as a successor
                        successorMap.computeIfAbsent(predId, k -> new ArrayList<>()).add(task);
                    } else {
                        log.warn("Task {} lists non-existent predecessor ID: {}. Ignoring this dependency.", task.getId(), predId);
                    }
                }
            }
        }
        return successorMap;
    }

    /**
     * Calculates the Earliest Start Time (EST/FAZ) and Earliest Finish Time (EFT/FEZ).
     * This pass determines the earliest possible time each task can start and finish
     * without violating precedence constraints.
     *
     * Uses Kahn's algorithm approach (based on in-degrees and a queue) to process tasks
     * in a topological order, which is necessary for correct EST/EFT calculation in a Directed Acyclic Graph (DAG).
     */
    private void calculateForwardPass(List<Task> tasks, Map<String, Task> taskMap, Map<String, List<Task>> successorMap) {
        log.debug("Starting Forward Pass calculation.");

        Map<String, Integer> inDegree = new HashMap<>();
        Queue<Task> queue = initializeForwardPassState(tasks, inDegree);

        int processedCount = 0;
        while (!queue.isEmpty()) {
            Task current = queue.poll();
            processedCount++;
            log.debug("Forward Pass: Processing task {} (EFT={})", current.getId(), current.getEarliestFinishDate());

            processForwardSuccessors(current, successorMap, inDegree, queue);
        }

        // Checks for cycles (if not all tasks were processed)
        if (processedCount != tasks.size()) {
            log.error("Forward Pass Error: Processed {} tasks, but expected {}. Possible cycle detected in dependencies.", processedCount, tasks.size());
        }
        log.debug("Forward Pass calculation finished.");
    }

    /**
     * Initializes the queue and in-degree map for the forward pass,
     * In-degree represents the number of direct predecessors for a task.
     * Tasks with an in-degree of 0 can start at the beginning of the project (time 0).
     * Sets initial EST/EFT for these starting tasks.
     * @return The initialized queue containing only the starting tasks.
     */
    private Queue<Task> initializeForwardPassState(List<Task> tasks, Map<String, Integer> inDegree) {
        Queue<Task> queue = new LinkedList<>();
        // Calculates initial in-degrees
        tasks.forEach(task -> {
            task.setEarliestStartDate(0); // Resets values
            task.setEarliestFinishDate(0);
            inDegree.put(task.getId(), task.getPredecessorIds() != null ? task.getPredecessorIds().size() : 0);
        });

        // Finds starting tasks (in-degree 0) and initializes them
        for (Task task : tasks) {
            if (inDegree.get(task.getId()) == 0) {
                task.setEarliestStartDate(0);
                task.setEarliestFinishDate(task.getDuration());
                queue.add(task);
                log.debug("Initialized start task {}: EST=0, EFT={}", task.getId(), task.getEarliestFinishDate());
            }
        }
        return queue;
    }

    /**
     * Processes the successors of a completed task (`current`) during the forward pass.
     ** Formula: Successors EST = max(Successor's current EST, Predecessor's EFT)
     ** Formula: Successors EFT = Successors new EST + Successors Duration
     * Decrements the in-degree of each successor. If a successors in-degree reaches 0,
     * all its predecessors have been processed, and it can be added to the queue.
     */
    private void processForwardSuccessors(Task current, Map<String, List<Task>> successorMap, Map<String, Integer> inDegree, Queue<Task> queue) {
        List<Task> successors = successorMap.getOrDefault(current.getId(), Collections.emptyList());

        for (Task successor : successors) {
            // Updates successors EST: max(current EST, predecessors EFT)
            int potentialEst = Math.max(successor.getEarliestStartDate(), current.getEarliestFinishDate());

            if (potentialEst > successor.getEarliestStartDate()) {
                successor.setEarliestStartDate(potentialEst);
                successor.setEarliestFinishDate(potentialEst + successor.getDuration());
                log.debug("  Updated successor {} EST to {} (from predecessor {}), new EFT = {}", successor.getId(), potentialEst, current.getId(), successor.getEarliestFinishDate());
            }

            // Decreases in-degree of the successor
            int newInDegree = inDegree.get(successor.getId()) - 1;
            inDegree.put(successor.getId(), newInDegree);

            // If in-degree becomes 0, adds successor to the queue
            if (newInDegree == 0) {
                queue.add(successor);
                log.debug("  Added successor {} to forward pass queue.", successor.getId());
            }
        }
    }

    /**
     * Calculates the Latest Start Time (LST/SAZ) and Latest Finish Time (LFT/SEZ).
     * This pass determines the latest possible time each task can start and finish
     * without delaying the overall project completion date (determined by the forward pass).
     *
     * Uses a similar approach to the forward pass but works backward from terminal tasks
     * (tasks with no successors) using out-degrees and a queue (or stack).
     */
    private void calculateBackwardPass(List<Task> tasks, Map<String, Task> taskMap, Map<String, List<Task>> successorMap) {
        log.debug("Starting Backward Pass calculation.");

        // Firstly initializes LFT/LST based on Max EFT
        final int projectFinishTime = initializeBackwardPassTasks(tasks);

        // Secondly builds predecessor map and initializes state for backward processing
        Map<String, List<Task>> predecessorMap = new HashMap<>();
        Map<String, Integer> outDegree = new HashMap<>();
        Queue<Task> queue = initializeBackwardPassState(tasks, successorMap, predecessorMap, outDegree, projectFinishTime);

        // Thirdly processes tasks from the queue (working backwards)
        int processedCount = 0;

        while (!queue.isEmpty()) {
            Task current = queue.poll();
            processedCount++;
            log.debug("Backward Pass: Processing task {} (LST={})", current.getId(), current.getLatestStartDate());

            // Processes predecessors and updates queue
            processBackwardPredecessors(current, predecessorMap, outDegree, queue);
        }

        if (processedCount != tasks.size()) {
            log.error("Backward Pass Error: Processed {} tasks, but expected {}. Possible issue.", processedCount, tasks.size());
        }
        log.debug("Backward Pass calculation finished.");
    }

    /**
     * Finds the project finish time (Max EFT from forward pass) and sets initial LFT/LST
     * for all tasks. The project finish time serves as the initial LFT constraint for all
     * terminal tasks.
     ** Formula: Initial LFT = Max(EFT of all tasks)
     ** Formula: Initial LST = Initial LFT - Task Duration
     * @return The calculated project finish time (Max EFT).
     */
    private int initializeBackwardPassTasks(List<Task> tasks) {
        int maxEFT = 0;

        for (Task task : tasks) {
            maxEFT = Math.max(maxEFT, task.getEarliestFinishDate());
        }
        log.debug("Project Max EFT (used for initial LFT): {}", maxEFT);
        final int projectFinishTime = maxEFT;

        tasks.forEach(task -> {
            task.setLatestFinishDate(projectFinishTime);
            task.setLatestStartDate(projectFinishTime - task.getDuration());
        });
        return projectFinishTime;
    }

    /**
     * Builds the predecessor map (reverse of successor map), calculates out-degrees
     * (number of direct successors), and initializes the queue with terminal tasks
     * (out-degree 0) for the backward pass.
     * @return The initialized queue containing only the terminal tasks.
     */
    private Queue<Task> initializeBackwardPassState(List<Task> tasks, Map<String, List<Task>> successorMap,
            Map<String, List<Task>> predecessorMap, Map<String, Integer> outDegree, int projectFinishTime) {
        Queue<Task> queue = new LinkedList<>();

        // Initializes predecessorMap and calculates out-degrees
        tasks.forEach(task -> {
            predecessorMap.put(task.getId(), new ArrayList<>());
            outDegree.put(task.getId(), successorMap.getOrDefault(task.getId(), Collections.emptyList()).size());
        });

        // Populates predecessor map
        for (Task task : tasks) {
            for (Task successor : successorMap.getOrDefault(task.getId(), Collections.emptyList())) {
                // Note: If successor ID doesn't exist in the map (corrupt data?), this would throw NullPointerException.
                predecessorMap.get(successor.getId()).add(task);
            }
        }

        // Finds terminal tasks (out-degree 0) and add to queue
        for (Task task : tasks) {
            if (outDegree.get(task.getId()) == 0) {
                // LFT/LST for terminal tasks are already set correctly in initializeBackwardPassTasks
                queue.add(task);
                log.debug("Initialized end task {}: LFT={}, LST={}", task.getId(), task.getLatestFinishDate(), task.getLatestStartDate());
            }
        }
        return queue;
    }

    /**
     * Processes the predecessors of a task (`current`) during the backward pass.
     ** Formula: Predecessor's LFT = min(Predecessor's current LFT, Successor's LST)
     ** Formula: Predecessor's LST = Predecessor's new LFT - Predecessor's Duration
     * Decrements the out-degree of each predecessor. If a predecessor's out-degree reaches 0,
     * all its successors have imposed their latest time constraints, and it can be added to the queue.
     */
    private void processBackwardPredecessors(Task current, Map<String, List<Task>> predecessorMap,
                                            Map<String, Integer> outDegree, Queue<Task> queue) {
        List<Task> predecessors = predecessorMap.getOrDefault(current.getId(), Collections.emptyList());

        for (Task predecessor : predecessors) {
            // Updates predecessors LFT: min(current LFT, successor's LST)
            int potentialLft = Math.min(predecessor.getLatestFinishDate(), current.getLatestStartDate());

            if (potentialLft < predecessor.getLatestFinishDate()) {
                predecessor.setLatestFinishDate(potentialLft);
                predecessor.setLatestStartDate(potentialLft - predecessor.getDuration());
                log.debug("  Updated predecessor {} LFT to {} (from successor {}), new LST = {}", predecessor.getId(), potentialLft, current.getId(), predecessor.getLatestStartDate());
            }

            // Decreases out-degree of the predecessor
            int newOutDegree = outDegree.get(predecessor.getId()) - 1;
            outDegree.put(predecessor.getId(), newOutDegree);

            // If out-degree becomes 0, adds predecessor to the queue
            if (newOutDegree == 0) {
                queue.add(predecessor);
                log.debug("  Added predecessor {} to backward pass queue.", predecessor.getId());
            }
        }
    }

    /**
     * Calculates Total Float (TF/GP), Free Float (FF/FP), and identifies the critical path.
     * This must be run after both forward and backward passes are complete.
     */
    private void calculateFloatsAndCriticalPath(List<Task> tasks, Map<String, Task> taskMap, Map<String, List<Task>> successorMap) {
        log.debug("Starting Float and Critical Path calculation.");

        for (Task task : tasks) {
            // Total Float (GP): The maximum time a task can be delayed without delaying the project end date
            //* Formula: TF = LST - EST (or LFT - EFT)
            int totalFloat = task.getLatestStartDate() - task.getEarliestStartDate();
            task.setTotalFloat(totalFloat);

            // Free Float (FP): The maximum time a task can be delayed without delaying the EST of any of its immediate successors
            //* Formula: FF = min(EST of all successors) - EFT of current task
            int minSuccessorEST = Integer.MAX_VALUE;
            List<Task> successors = successorMap.getOrDefault(task.getId(), Collections.emptyList());

            if (successors.isEmpty()) {
                // For tasks with no successors (terminal tasks), free float equals total float
                // The constraint is the project end date (LFT)
                minSuccessorEST = task.getLatestFinishDate(); // Use LFT as the effective "start" time of the next phase (project end)
            } else {
                for (Task successor : successors) {
                    minSuccessorEST = Math.min(minSuccessorEST, successor.getEarliestStartDate());
                }
            }
            int freeFloat = minSuccessorEST - task.getEarliestFinishDate();
            task.setFreeFloat(Math.max(0, freeFloat)); // Free float cannot be negative

            // Critical Path: A path through the network where all tasks have zero Total Float
            // Any delay on a critical task directly delays the entire project!
            boolean isCritical = (totalFloat == 0);
            task.setCritical(isCritical);

            log.debug("Task {}: TF={}, FF={}, Critical={}", task.getId(), totalFloat, task.getFreeFloat(), isCritical);
        }
        log.debug("Float and Critical Path calculation finished.");
    }
}
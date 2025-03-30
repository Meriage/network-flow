# Network Flow Diagram Generator

## Project Goal

Develop a software application that automatically generates a network diagram
(PERT/CPM chart) for project management purposes based on task data provided in a
structured format. This aims to replace the time-intensive manual creation of
such diagrams.

## Core Concepts

1. **Data Input:**
    * The application reads project task data from a **JSON** file (for example: `src/main/resources/data/tasks.json`)
    * Each task requires the attributes: `id` (String), `description` (String), `duration` (int), and `predecessorIds` (List<String>)

2. **Core Logic (Critical Path Method - CPM):**
    * An algorithm processes the task data to calculate project management metrics:
        * Earliest Start Time (EST / FAZ - Früheste Anfangszeit)
        * Earliest Finish Time (EFT / FEZ - Früheste Endzeit)
        * Latest Start Time (LST / SAZ - Späteste Anfangszeit)
        * Latest Finish Time (LFT / SEZ - Späteste Endzeit)
        * Total Float (TF / GP - Gesamtpuffer)
        * Free Float (FF / FP - Freier Puffer)
    * The algorithm also identifies the Critical Path (tasks with zero Total Float/Gesamtpuffer)

3. **Output / Visualization:**
    * The results are displayed in a web interface using **German** labels (i mean, its a german school project)
    * A **network diagram** is rendered using **Mermaid.js**:
        * Tasks are represented as nodes containing multiple lines: Description, calculated times (FAZ, FEZ, SAZ, SEZ), and values for Duration (Dauer), Total Float (GP), and Free Float (FP)
        * Dependencies are shown as arrows labeled with the predecessors duration
        * The Critical Path (nodes and links) is visually highlighted
    * A **data table** below the diagram displays the input and calculated values for each task

4. **Tech Stack:**
    * **Backend:** Java, Spring Boot
    * **Frontend:** Thymeleaf
    * **Key Libraries Used:**
        * `Jackson`: For parsing the input JSON data
        * `Lombok`: To reduce boilerplate code in the data model
        * `Mermaid.js`: For rendering the network diagram in the browser
    * **(Considered but not used):** JGraphT (for explicit graph representation), CSV parsing libraries

## How to Run

1. **Prerequisites:** Ensure you have Java (JDK 21 recommended) and Apache Maven installed
2. **Clone the Repository:** Get a local copy of the project (`git clone https://github.com/Meriage/network-flow.git`)
3. **Navigate to Project Root:** Open a terminal and change directory to the projects root folder (where the `pom.xml` file is located)
4. **Run the Application:** Execute the following Maven command:
    mvn spring-boot:run
5. The application will start, and the embedded Tomcat server will run on port 8080 (`http://localhost:8080`)

## How to Use

1. **View the Diagram and Data:** The page will display the generated network diagram (using Mermaid.js) and a table with the calculated task data
2. **Modify Input Data:** To look at a different project, modify the contents of the input JSON file located at:
    `src/main/resources/data/tasks.json`
    Ensure the file adheres to the required JSON structure (an array of task objects with `id`, `description`, `duration`, and `predecessorIds`)
3. **Restart the Application:** After modifying `tasks.json`, it should automatically refresh
    If this does not happen, stop the running application (`Ctrl+C` in the terminal) and restart it to see the updated diagram

## Initial Development Roadmap

1. **[x] Setup Project Structure:** Create a basic Spring Boot project structure with Spring Initializr
2. **[x] Define Data Model:** Create Java classes to represent Tasks
3. **[x] Implement Data Parsing:** Write code to read and parse the input file (JSON)
4. **[x] Implement CPM Algorithm:**
    * [x] Represent task dependencies (implicitly via IDs, processed into maps)
    * [x] Implement the forward pass calculation (EST, EFT)
    * [x] Implement the backward pass calculation (LST, LFT)
    * [x] Calculate buffers (Total Float, Free Float)
    * [x] Implement the critical path calculation
5. **[x] Develop Basic Web Interface:**
    * [x] Create Spring Boot controller to handle requests
    * [x] Create Thymeleaf template to display basic task data and calculated results
6. **[x] Integrate Visualization:**
    * [x] Choose and integrate a JavaScript diagramming library (Mermaid.js)
    * [x] Pass the calculated data from the Spring Boot backend to the frontend in Mermaid syntax
    * [x] Render the network diagram in the web interface using Thymeleaf and Mermaid.js
7. **[x] Refinement & Testing:** Initial refinements (layout, translation, node display) and manual testing performed

## Development Summary and Decisions

This section aims to outline the steps taken during development and the reasoning behind technical decisions.

1. **Project Setup:**
    * The project was initialized using Spring Initializr (easy starting point)
    * Core dependencies added: `Spring Web` (for web capabilities), `Thymeleaf` (for server-side HTML templating), and `Lombok` (to reduce boilerplate code in model classes)

2. **Data Modeling (`model` package):**
    * A `Task.java` class was created to represent project activities, including fields for input data (ID, Description, Duration, Predecessors) and calculated CPM values (EST, EFT, LST, LFT, Floats, Critical status)
    * Lombok (`@Data`, `@NoArgsConstructor`) was used to minimize getter/setter/constructor code
    * JSON was chosen as the input data format for its flexibility (can potentially represent more complex data structures)

3. **Data Parsing (`service` package):**
    * `ProjectDataService` was implemented to handle reading the input data
    * It uses Spring Boot's auto-configured Jackson `ObjectMapper` to parse the JSON file (for example `src/main/resources/data/tasks.json`) into a `List<Task>`
    * Classpath resource loading (`ClassPathResource`) ensures the data file is found reliably

4. **CPM Algorithm (`service` package):**
    * `CpmService` encapsulates the core Critical Path Method logic
    * This service was refactored multiple times starting from an initial implementation to improve **Separation of Concerns** and **Efficiency**
    (because it got really, really complicated really, really fast)
    * **Key Refinements:**
        * A public `performCpmCalculations` method orchestrates the calculation sequence (forward pass -> backward pass -> float calculation)
        * Helper maps (`taskMap`, `successorMap`) are built once at the start for efficient O(1) average time lookups during calculations
        * The main calculation steps (`calculateForwardPass`, `calculateBackwardPass`, `calculateFloatsAndCriticalPath`) are private methods, each focused on a distinct responsibility
        * Forward and backward passes were optimized using queue-based processing with in-degree/out-degree tracking, analogous to topological sorting algorithms suitable for Directed Acyclic Graphs (i would have never thought i would write something like this one day)

5. **Web Interface (`controller` & `templates`):**
    * **Architecture:** A standard Layered Architecture (Presentation-Service-Domain) was implemented
    * `ProjectController` was created to handle web requests (`@GetMapping("/")`), coordinating calls to `ProjectDataService` and `CpmService`
    * A Thymeleaf template (`network-view.html`) was developed in `src/main/resources/templates` to render the data provided by the controller
    * The initial version displayed the calculated task data in a simple HTML table, which was later supplemented by the graphical diagram

6. **Visualization (`controller` & `templates`):**
    * **Library Choice:** `Mermaid.js` was selected for rendering the network diagram due to its simplicity in generating graph syntax from backend data
    * **Integration:**
        * The `ProjectController` was enhanced with a `generateMermaidSyntax` method to convert the calculated `List<Task>` into Mermaid's text-based graph definition
        * Mermaid node labels were structured using `<br>` tags to display multiple lines within each node: **Description**, a separator, **times (FAZ, FEZ, SAZ, SEZ)**, another separator, and finally **Duration (Dauer), Total Float (GP), and Free Float (FP)**, reflecting the German terminology
        * Styling was applied within the generated Mermaid syntax to highlight critical nodes (fill color) and critical links (thicker, colored line)
        * The `network-view.html` template was updated to include the Mermaid.js library (via CDN) and uses Thymeleaf's `th:utext` attribute to embed the generated syntax into a `<div class="mermaid">`, enabling rendering

package com.serenity_temp.example.conv.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.serenity_temp.example.conv.protocol.Messages;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * LLM Client Actor that communicates with Python process running Qwen model
 */
public class LlmClientActor extends AbstractBehavior<Object> {

    // State management
    private enum State {
        UNINITIALIZED,
        STARTING_PYTHON,
        LOADING_MODEL,
        READY,
        FAILED
    }

    // Message classes
    public static final class Generate {
        public final Messages.LlmRequest req;
        public final ActorRef<Messages.Msg> replyTo;
        public final ActorRef<ConsoleActor.Command> console;
        public Generate(Messages.LlmRequest req, ActorRef<Messages.Msg> replyTo, ActorRef<ConsoleActor.Command> console) {
            this.req = req;
            this.replyTo = replyTo;
            this.console = console;
        }
    }

    public static final class ValidateLanguage {
        public final String language;
        public final ActorRef<Messages.Msg> replyTo;
        public final ActorRef<ConsoleActor.Command> console;
        public ValidateLanguage(String language, ActorRef<Messages.Msg> replyTo, ActorRef<ConsoleActor.Command> console) {
            this.language = language;
            this.replyTo = replyTo;
            this.console = console;
        }
    }

    // Internal initialization message
    private static final class SelfInitialize {}

    // State and resources
    private State state = State.UNINITIALIZED;
    private Process pythonProcess;
    private BufferedWriter pythonInput;
    private BufferedReader pythonOutput;
    private BufferedReader pythonError;
    private final ObjectMapper objectMapper;
    private final String pythonScript;
    private final String modelId;
    private Thread errorReaderThread;

    public static Behavior<Object> create() {
        return Behaviors.setup(ctx -> {
            LlmClientActor actor = new LlmClientActor(ctx);
            // Trigger self-initialization after construction
            ctx.getSelf().tell(new SelfInitialize());
            return actor;
        });
    }

    private LlmClientActor(ActorContext<Object> ctx) {
        super(ctx);
        this.objectMapper = new ObjectMapper();
        // Defaults can be overridden by env vars
        this.pythonScript = System.getenv("LLM_SCRIPT_PATH") != null
            ? System.getenv("LLM_SCRIPT_PATH")
            : "llm_server_py.py"; // script lives in module root
        this.modelId = System.getenv("LLM_MODEL_ID") != null
            ? System.getenv("LLM_MODEL_ID")
            : "Qwen/Qwen2.5-3B-Instruct";

        getContext().getLog().info("LLM Actor created. Script: {}, Model: {}", pythonScript, modelId);
    }

    @Override
    public Receive<Object> createReceive() {
        return newReceiveBuilder()
            .onMessage(SelfInitialize.class, this::onSelfInitialize)
            .onMessage(Generate.class, this::onGenerate)
            .onMessage(ValidateLanguage.class, this::onValidateLanguage)
            .onSignal(PostStop.class, sig -> onPostStop())
            .build();
    }

    private Behavior<Object> onSelfInitialize(SelfInitialize msg) {
        if (state != State.UNINITIALIZED) {
            getContext().getLog().warn("Already initialized, state: {}", state);
            return this;
        }

        getContext().getLog().info("Starting initialization sequence...");
        try {
            state = State.STARTING_PYTHON;
            startPythonProcess();

            state = State.LOADING_MODEL;
            loadModel();

            boolean verified = verifyModel();
            if (verified) {
                state = State.READY;
                getContext().getLog().info("\u2713 LLM initialization complete - model ready!");
            } else {
                state = State.FAILED;
                getContext().getLog().error("\u2717 LLM verification failed");
            }
        } catch (Exception e) {
            state = State.FAILED;
            getContext().getLog().error("\u2717 Failed to initialize LLM", e);
            cleanupPythonProcess();
        }
        return this;
    }

    private void startPythonProcess() throws IOException {
        getContext().getLog().info("Starting Python process...");

        ProcessBuilder pb = new ProcessBuilder(
            "python3", "-u", pythonScript,
            "--model", modelId,
            "--mode", "interactive",
            "--use-cache"
        );
        pb.environment().put("PYTHONUNBUFFERED", "1");

        pythonProcess = pb.start();
        pythonInput = new BufferedWriter(new OutputStreamWriter(pythonProcess.getOutputStream()));
        pythonOutput = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));
        pythonError = new BufferedReader(new InputStreamReader(pythonProcess.getErrorStream()));

        startErrorStreamReader();

        String ready = pythonOutput.readLine();
        if (!"READY".equals(ready)) {
            throw new IOException("Python process failed to start. Got: " + ready);
        }
        getContext().getLog().info("\u2713 Python process started successfully");
    }

    private void loadModel() throws IOException {
        getContext().getLog().info("Loading model {}... (this may take a minute)", modelId);

        ObjectNode loadCmd = objectMapper.createObjectNode();
        loadCmd.put("command", "load_model");
        pythonInput.write(loadCmd.toString() + "\n");
        pythonInput.flush();

        String response = pythonOutput.readLine();
        JsonNode responseJson = objectMapper.readTree(response);
        if (!"loaded".equals(responseJson.path("status").asText())) {
            throw new IOException("Failed to load model: " + response);
        }
        getContext().getLog().info("\u2713 Model loaded successfully");
    }

    private boolean verifyModel() {
        try {
            getContext().getLog().info("Verifying model with test prompt...");

            ObjectNode testCmd = objectMapper.createObjectNode();
            testCmd.put("command", "generate");
            testCmd.put("prompt", "Hello");
            testCmd.put("max_tokens", 5);

            pythonInput.write(testCmd.toString() + "\n");
            pythonInput.flush();

            String response = pythonOutput.readLine();
            JsonNode responseJson = objectMapper.readTree(response);
            boolean success = responseJson.has("text") && responseJson.has("status");
            getContext().getLog().info("\u2713 Model verification {}: {}",
                success ? "succeeded" : "failed",
                responseJson.path("text").asText());
            return success;
        } catch (Exception e) {
            getContext().getLog().error("Model verification failed", e);
            return false;
        }
    }

    private void startErrorStreamReader() {
        errorReaderThread = new Thread(() -> {
            try {
                String line;
                while ((line = pythonError.readLine()) != null) {

                    if (!line.contains("INFO") && !line.contains("WARNING")) {
                        // Use System.err instead of actor context from background thread
                        System.err.println("Python stderr: " + line);
                    }
                }
            } catch (IOException e) {
                // Process likely terminated
            }
        }, "python-stderr-reader");
        errorReaderThread.setDaemon(true);
        errorReaderThread.start();
    }

    private Behavior<Object> onGenerate(Generate gen) {
        if (state != State.READY) {
            String error = "LLM not ready. Current state: " + state;
            gen.console.tell(new ConsoleActor.Print("[ERROR] " + error));
            gen.replyTo.tell(new Messages.LlmError(error, gen.console));
            if (state == State.UNINITIALIZED) {
                getContext().getSelf().tell(new SelfInitialize());
                gen.console.tell(new ConsoleActor.Print("[INFO] Starting LLM initialization..."));
            }
            return this;
        }

        try {
            Messages.LlmRequest req = gen.req;

            ObjectNode request = objectMapper.createObjectNode();
            request.put("command", "generate");
            request.put("prompt", req.latestUser());
            if (req.targetLang() != null && !req.targetLang().isEmpty()) {
                request.put("target_lang", req.targetLang());
            }
            if (req.history() != null && !req.history().isEmpty()) {
                ArrayNode historyArray = objectMapper.createArrayNode();
                for (Messages.ChatTurn turn : req.history()) {
                    historyArray.add(turn.content());
                }
                request.set("history", historyArray);
            }
            request.put("max_tokens", 256);
            request.put("temperature", 0.7);

            String jsonRequest = request.toString();
            getContext().getLog().debug("Sending to Python: {}", jsonRequest);

            pythonInput.write(jsonRequest + "\n");
            pythonInput.flush();

            String response = pythonOutput.readLine();
            if (response == null) {
                throw new IOException("Python process returned null (may have crashed)");
            }
            getContext().getLog().debug("Received from Python: {}", response);

            JsonNode responseJson = objectMapper.readTree(response);
            if (responseJson.has("text")) {
                String text = responseJson.path("text").asText();
                gen.console.tell(new ConsoleActor.Print("[LLM] " + text));
                gen.replyTo.tell(new Messages.LlmReply(text, gen.console));
            } else if (responseJson.has("error")) {
                throw new IOException("Python error: " + responseJson.path("error").asText());
            } else {
                throw new IOException("Invalid response from Python: " + response);
            }
        } catch (Exception e) {
            String error = "Generation failed: " + e.getMessage();
            getContext().getLog().error(error, e);
            gen.console.tell(new ConsoleActor.Print("[ERROR] " + error));
            gen.replyTo.tell(new Messages.LlmError(error, gen.console));

            if (pythonProcess != null && !pythonProcess.isAlive()) {
                state = State.FAILED;
                getContext().getLog().error("Python process died. Attempting restart...");
                cleanupPythonProcess();
                state = State.UNINITIALIZED;
                getContext().getSelf().tell(new SelfInitialize());
            }
        }
        return this;
    }

    private Behavior<Object> onValidateLanguage(ValidateLanguage val) {
        if (state != State.READY) {
            String error = "LLM not ready. Current state: " + state;
            val.console.tell(new ConsoleActor.Print("[ERROR] " + error));
            val.replyTo.tell(new Messages.LanguageValidationResult(val.language, false, error, val.console));
            if (state == State.UNINITIALIZED) {
                getContext().getSelf().tell(new SelfInitialize());
                val.console.tell(new ConsoleActor.Print("[INFO] Starting LLM initialization..."));
            }
            return this;
        }

        try {
            // Create a validation prompt for the LLM
            String validationPrompt = String.format(
                "Can you hold a decent conversation in '%s'? " +
                "Reply ONLY with 'YES' if you can continue the conversation in this language, " +
                "or 'NO: reason' if you cannot.",
                val.language
            );

            ObjectNode request = objectMapper.createObjectNode();
            request.put("command", "generate");
            request.put("prompt", validationPrompt);
            request.put("max_tokens", 50);  // Short response expected
            request.put("temperature", 0.1); // Low temperature for consistent answers

            String jsonRequest = request.toString();

            pythonInput.write(jsonRequest + "\n");
            pythonInput.flush();

            String response = pythonOutput.readLine();
            if (response == null) {
                throw new IOException("Python process returned null (may have crashed)");
            }

            JsonNode responseJson = objectMapper.readTree(response);
            if (responseJson.has("text")) {
                String llmResponse = responseJson.path("text").asText().trim();

                // Parse LLM response
                boolean isValid;
                String reason;
                
                if (llmResponse.toUpperCase().startsWith("YES")) {
                    isValid = true;
                    reason = "Language supported";
                    // val.console.tell(new ConsoleActor.Print("[VALIDATION] ✓ Language '" + val.language + "' is supported"));
                } else if (llmResponse.toUpperCase().startsWith("NO")) {
                    isValid = false;
                    // Extract reason after "NO:"
                    reason = llmResponse.length() > 3 ? llmResponse.substring(3).trim() : "Language not supported";
                    // val.console.tell(new ConsoleActor.Print("[VALIDATION] ✗ Language '" + val.language + "' not supported: " + reason));
                } else {
                    // Ambiguous response - be conservative
                    isValid = false;
                    reason = "Unclear validation response: " + llmResponse;
                    // val.console.tell(new ConsoleActor.Print("[VALIDATION] ? Unclear response for '" + val.language + "': " + llmResponse));
                }

                val.replyTo.tell(new Messages.LanguageValidationResult(val.language, isValid, reason, val.console));
                
            } else if (responseJson.has("error")) {
                throw new IOException("Python error: " + responseJson.path("error").asText());
            } else {
                throw new IOException("Invalid response from Python: " + response);
            }

        } catch (Exception e) {
            String error = "Language validation failed: " + e.getMessage();
            getContext().getLog().error(error, e);
            val.console.tell(new ConsoleActor.Print("[ERROR] " + error));
            val.replyTo.tell(new Messages.LanguageValidationResult(val.language, false, error, val.console));

            if (pythonProcess != null && !pythonProcess.isAlive()) {
                state = State.FAILED;
                getContext().getLog().error("Python process died. Attempting restart...");
                cleanupPythonProcess();
                state = State.UNINITIALIZED;
                getContext().getSelf().tell(new SelfInitialize());
            }
        }
        return this;
    }

    private Behavior<Object> onPostStop() {
        getContext().getLog().info("Shutting down LLM Actor...");
        cleanupPythonProcess();
        return this;
    }

    private void cleanupPythonProcess() {
        try {
            if (pythonInput != null) {
                try {
                    ObjectNode shutdownCmd = objectMapper.createObjectNode();
                    shutdownCmd.put("command", "shutdown");
                    pythonInput.write(shutdownCmd.toString() + "\n");
                    pythonInput.flush();
                    pythonInput.close();
                } catch (IOException ignored) {
                }
            }
            if (pythonProcess != null) {
                boolean terminated = pythonProcess.waitFor(5, TimeUnit.SECONDS);
                if (!terminated) {
                    getContext().getLog().warn("Python process didn't terminate gracefully, forcing...");
                    pythonProcess.destroyForcibly();
                }
            }
            if (pythonOutput != null) pythonOutput.close();
            if (pythonError != null) pythonError.close();
            if (errorReaderThread != null) errorReaderThread.interrupt();
            getContext().getLog().info("\u2713 Python process cleaned up");
        } catch (Exception e) {
            getContext().getLog().error("Error during cleanup", e);
        }
    }
}

# Conversational AI System with Akka Actors

A multi-language conversational AI system built with Akka Typed actors, designed for scalable and maintainable chat interactions.

## Architecture Overview

This system uses the Actor Model (Akka Typed) to create a clean separation of concerns:

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Main (CLI)    │───▶│  SessionActor    │───▶│ LlmClientActor  │
│                 │    │  (State Mgmt)    │    │  (LLM Service)  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         │                       ▼                       │
         │              ┌─────────────────┐              │
         └─────────────▶│  ConsoleActor   │◀─────────────┘
                        │   (I/O Mgmt)    │
                        └─────────────────┘
```

## Project Structure

```
codebase/
├── src/main/java/com/serenity_temp/example/
│   ├── conv/
│   │   ├── actors/
│   │   │   ├── ConsoleActor.java      # Handles all console I/O
│   │   │   ├── SessionActor.java      # Manages conversation state
│   │   │   └── LlmClientActor.java    # Interfaces with LLM service
│   │   ├── protocol/
│   │   │   └── Messages.java          # Message definitions
│   │   └── app/
│   │       └── Main.java              # Application entry point
├── pom.xml                            # Maven dependencies
└── README.md                          # This file
```

## Actor Responsibilities

### 1. **SessionActor** (Conversation State Manager)
- Maintains target language and conversation history
- Processes user inputs and language selection
- Coordinates between console and LLM actors
- Manages conversation flow and context

### 2. **ConsoleActor** (I/O Manager)
- Handles all console output (print, error, prompts)
- Ensures thread-safe console operations
- Provides clean separation of I/O concerns
- Easily replaceable with web/HTTP interface

### 3. **LlmClientActor** (LLM Service Interface)
- Communicates with external LLM service via HTTP
- Currently uses fake responses for testing
- Handles async HTTP requests and responses
- Manages LLM service errors and timeouts

### 4. **Main** (System Bootstrap)
- Creates and wires all actors
- Manages actor system lifecycle
- Handles CLI input loop
- Coordinates system shutdown

## Message Protocol

All actors communicate through well-defined message types:

- `Start(console)` - Initialize conversation
- `ChooseLanguage(lang, console)` - Set target language
- `UserInput(text, console)` - Process user message
- `LlmReply(text, console)` - LLM response
- `LlmError(reason, console)` - LLM error handling

## Dependencies

- **Akka Typed 2.6.21** - Actor system framework
- **Logback 1.2.3** - Logging framework
- **OkHttp 4.12.0** - HTTP client for LLM service
- **Jackson 2.17.1** - JSON processing
- **Java 17+** - Runtime requirement

## Startup Commands

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Build the Project
```bash
mvn clean package
```

### Run the Conversation System
```bash
mvn exec:java -Dexec.mainClass=com.serenity_temp.example.conv.app.Main
```

### Interactive Usage
1. **Start the application** - Run the command above
2. **Choose language** - Enter language code (e.g., `es`, `fr`, `de`)
3. **Start chatting** - Type messages and see responses
4. **Exit** - Type `exit` or `quit`, or press Ctrl+C

### Example Session
```
[CLI] Starting conversation system...
[CLI] Conversation started. Enter language code (e.g., es, fr, de):
Which language code (e.g., es, fr, de)?
Lang> es
[DEBUG] Language chosen: es
[DEBUG] Sending initial hello to LLM...
Assistant: [FAKE] (es) Hello!
> hola
Assistant: [FAKE] (es) hola
> exit
[CLI] Shutting down...
```

## Development Features

### Current Implementation
- ✅ Multi-language conversation support
- ✅ Actor-based architecture
- ✅ Clean message protocol
- ✅ Console I/O management
- ✅ Fake LLM responses for testing
- ✅ Graceful shutdown handling

### Ready for Extension
- 🔄 **Real LLM Integration** - HTTP endpoints ready
- 🔄 **Web Interface** - Replace ConsoleActor with HTTP handlers
- 🔄 **Persistence** - Add conversation history storage
- 🔄 **Multi-user** - Session management per user
- 🔄 **Advanced Features** - File uploads, voice, etc.

## Configuration

### LLM Service Integration
To enable real LLM responses, uncomment the HTTP implementation in `LlmClientActor.java` and ensure your LLM service is running at `http://127.0.0.1:8000/chat`.

Expected LLM service API:
```json
POST /chat
{
  "targetLang": "es",
  "history": [{"role": "system", "content": "..."}],
  "latestUser": "Hello!"
}

Response:
{
  "text": "¡Hola! ¿Cómo estás?"
}
```

### Debug Mode
Debug output is currently enabled. To disable, remove `[DEBUG]` print statements from:
- `SessionActor.java`
- `ConsoleActor.java` 
- `LlmClientActor.java`
- `Main.java`

## Architecture Benefits

1. **Scalability** - Each actor handles messages independently
2. **Maintainability** - Clear separation of concerns
3. **Testability** - Actors can be tested in isolation
4. **Extensibility** - Easy to add new features or interfaces
5. **Fault Tolerance** - Actor supervision and error handling
6. **Concurrency** - Built-in thread safety with actor model

## Future Enhancements

- **Web API** - REST endpoints for web clients
- **WebSocket** - Real-time chat interface
- **Database** - Persistent conversation storage
- **Authentication** - User management and sessions
- **Multiple LLMs** - Support for different AI models
- **Rich Media** - Image, file, and voice support
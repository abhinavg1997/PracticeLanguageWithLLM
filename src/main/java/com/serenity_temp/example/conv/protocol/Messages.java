package com.serenity_temp.example.conv.protocol;

import akka.actor.typed.ActorRef;
import com.serenity_temp.example.conv.actors.ConsoleActor;
import java.util.List;
import java.util.Objects;

public final class Messages {
    private Messages() {}

    public interface Msg {}

    // Add console reference to messages that need output
    public static final class Start implements Msg {
        public final ActorRef<ConsoleActor.Command> console;
        public Start(ActorRef<ConsoleActor.Command> console) {
            this.console = console;
        }
    }

    public static final class ChooseLanguage implements Msg {
        private final String targetLang;
        public final ActorRef<ConsoleActor.Command> console;
        public ChooseLanguage(String targetLang, ActorRef<ConsoleActor.Command> console) {
            this.targetLang = Objects.requireNonNull(targetLang);
            this.console = console;
        }
        public String targetLang() { return targetLang; }
    }

    public static final class UserInput implements Msg {
        private final String text;
        public final ActorRef<ConsoleActor.Command> console;
        public UserInput(String text, ActorRef<ConsoleActor.Command> console) { 
            this.text = Objects.requireNonNull(text);
            this.console = console;
        }
        public String text() { return text; }
    }

    public static final class LlmReply implements Msg {
        private final String text;
        public final ActorRef<ConsoleActor.Command> console;
        public LlmReply(String text, ActorRef<ConsoleActor.Command> console) { 
            this.text = Objects.requireNonNull(text);
            this.console = console;
        }
        public String text() { return text; }
    }

    public static final class LlmError implements Msg {
        private final String reason;
        public final ActorRef<ConsoleActor.Command> console;
        public LlmError(String reason, ActorRef<ConsoleActor.Command> console) { 
            this.reason = Objects.requireNonNull(reason);
            this.console = console;
        }
        public String reason() { return reason; }
    }

    // Value types remain the same
    public static final class ChatTurn {
        private final String role;
        private final String content;
        public ChatTurn(String role, String content) {
            this.role = Objects.requireNonNull(role);
            this.content = Objects.requireNonNull(content);
        }
        public String role() { return role; }
        public String content() { return content; }
    }

    public static final class LlmRequest {
        private final String targetLang;
        private final List<ChatTurn> history;
        private final String latestUser;
        public LlmRequest(String targetLang, List<ChatTurn> history, String latestUser) {
            this.targetLang = Objects.requireNonNull(targetLang);
            this.history = Objects.requireNonNull(history);
            this.latestUser = Objects.requireNonNull(latestUser);
        }
        public String targetLang() { return targetLang; }
        public List<ChatTurn> history() { return history; }
        public String latestUser() { return latestUser; }
    }
}
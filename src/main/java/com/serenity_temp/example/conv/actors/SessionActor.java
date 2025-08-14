// Updated SessionActor.java - Uses ConsoleActor for output
package com.serenity_temp.example.conv.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.serenity_temp.example.conv.protocol.Messages;
import java.util.ArrayList;
import java.util.List;

public class SessionActor extends AbstractBehavior<Messages.Msg> {

    public static Behavior<Messages.Msg> create(ActorRef<Object> llmClient) {
        return Behaviors.setup(ctx -> new SessionActor(ctx, llmClient));
    }

    private final ActorRef<Object> llmClient;
    private String targetLang;
    private final List<Messages.ChatTurn> history;
    private ActorRef<ConsoleActor.Command> console;

    private SessionActor(ActorContext<Messages.Msg> ctx, ActorRef<Object> llmClient) {
        super(ctx);
        this.llmClient = llmClient;
        this.history = new ArrayList<>();
    }

    @Override
    public Receive<Messages.Msg> createReceive() {
        return newReceiveBuilder()
            .onMessage(Messages.Start.class, this::onStart)
            .onMessage(Messages.ChooseLanguage.class, this::onChoose)
            .onMessage(Messages.UserInput.class, this::onUser)
            .onMessage(Messages.LlmReply.class, this::onLlmReply)
            .onMessage(Messages.LlmError.class, this::onLlmError)
            .build();
    }

    private Behavior<Messages.Msg> onStart(Messages.Start m) {
        System.out.println("[DEBUG] SessionActor received Start message");
        this.console = m.console;
        console.tell(new ConsoleActor.Print("Which language code (e.g., es, fr, de)?"));
        console.tell(new ConsoleActor.Prompt("Lang> "));
        System.out.println("[DEBUG] SessionActor sent prompts to console");
        return this;
    }

    private Behavior<Messages.Msg> onChoose(Messages.ChooseLanguage m) {
        this.console = m.console;
        this.targetLang = m.targetLang();
        history.add(new Messages.ChatTurn("system", 
            "You are a helpful assistant. Always reply in " + targetLang + "."));
        
        console.tell(new ConsoleActor.Print("[DEBUG] Language chosen: " + targetLang));
        console.tell(new ConsoleActor.Print("[DEBUG] Sending initial hello to LLM..."));
        
        history.add(new Messages.ChatTurn("user", "Hello!"));
        requestLlm("Hello!");
        return this;
    }

    private Behavior<Messages.Msg> onUser(Messages.UserInput m) {
        this.console = m.console;
        if (targetLang == null) {
            console.tell(new ConsoleActor.Print("Please choose a language first."));
            console.tell(new ConsoleActor.Prompt("> "));
            return this;
        }
        history.add(new Messages.ChatTurn("user", m.text()));
        requestLlm(m.text());
        return this;
    }

    private void requestLlm(String latestUser) {
        Messages.LlmRequest req = new Messages.LlmRequest(targetLang, 
            new ArrayList<>(history), latestUser);
        ActorRef<Messages.Msg> replyTo = getContext().getSelf();
        llmClient.tell(new LlmClientActor.Generate(req, replyTo, console));
    }

    private Behavior<Messages.Msg> onLlmReply(Messages.LlmReply r) {
        this.console = r.console;
        history.add(new Messages.ChatTurn("assistant", r.text()));
        console.tell(new ConsoleActor.Print("\nAssistant: " + r.text()));
        console.tell(new ConsoleActor.Prompt("> "));
        return this;
    }

    private Behavior<Messages.Msg> onLlmError(Messages.LlmError e) {
        this.console = e.console;
        console.tell(new ConsoleActor.PrintError("LLM error: " + e.reason()));
        console.tell(new ConsoleActor.Prompt("> "));
        return this;
    }
}
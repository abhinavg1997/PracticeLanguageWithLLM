// ConsoleActor.java - Dedicated actor for handling all console I/O
package com.serenity_temp.example.conv.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class ConsoleActor extends AbstractBehavior<ConsoleActor.Command> {
    
    public interface Command {}
    
    public static final class Print implements Command {
        public final String message;
        public final boolean newLine;
        public Print(String message) { 
            this(message, true); 
        }
        public Print(String message, boolean newLine) {
            this.message = message;
            this.newLine = newLine;
        }
    }
    
    public static final class PrintError implements Command {
        public final String message;
        public PrintError(String message) { 
            this.message = message; 
        }
    }
    
    public static final class Prompt implements Command {
        public final String prompt;
        public Prompt(String prompt) { 
            this.prompt = prompt; 
        }
    }
    
    public static Behavior<Command> create() {
        return Behaviors.setup(ConsoleActor::new);
    }
    
    private ConsoleActor(ActorContext<Command> context) {
        super(context);
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(Print.class, this::onPrint)
            .onMessage(PrintError.class, this::onPrintError)
            .onMessage(Prompt.class, this::onPrompt)
            .build();
    }
    
    private Behavior<Command> onPrint(Print cmd) {
        if (cmd.newLine) {
            System.out.println(cmd.message);
        } else {
            System.out.print(cmd.message);
        }
        System.out.flush();
        return this;
    }
    
    private Behavior<Command> onPrintError(PrintError cmd) {
        System.err.println(cmd.message);
        System.err.flush();
        return this;
    }
    
    private Behavior<Command> onPrompt(Prompt cmd) {
        System.out.print(cmd.prompt);
        System.out.flush();
        return this;
    }
}

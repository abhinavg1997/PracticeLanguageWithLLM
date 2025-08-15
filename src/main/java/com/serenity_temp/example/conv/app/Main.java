// Updated Main.java - Creates ConsoleActor and passes reference
package com.serenity_temp.example.conv.app;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import com.serenity_temp.example.conv.actors.ConsoleActor;
import com.serenity_temp.example.conv.actors.LlmClientActor;
import com.serenity_temp.example.conv.actors.SessionActor;
import com.serenity_temp.example.conv.protocol.Messages;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        // Create a holder for the console actor reference
        CompletableFuture<ActorRef<ConsoleActor.Command>> consoleFuture = new CompletableFuture<>();
        //start message
        System.out.println("[CLI] Starting conversation system...");
        ActorSystem<Messages.Msg> system = ActorSystem.create(
            Behaviors.setup(ctx -> {
                // Create the console actor first
                ActorRef<ConsoleActor.Command> console = 
                    ctx.spawn(ConsoleActor.create(), "console");

                // Make it available to main thread
                consoleFuture.complete(console);

                // Create other actors inside setup
                ActorRef<Object> llm = ctx.spawn(LlmClientActor.create(), "llm");

                // Root behavior is the session
                return SessionActor.create(llm);
            }),
            "conv-system"
        );
        try {
            // Get the console actor reference
            ActorRef<ConsoleActor.Command> console = consoleFuture.get(5, TimeUnit.SECONDS);
            system.tell(new Messages.Start(console));
            
            // Allow time for initialization
            Thread.sleep(100);
            
            try (Scanner sc = new Scanner(System.in)) {
                // Start with language selection - SessionActor will prompt
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                        break;
                    }
                    system.tell(new Messages.UserInput(line, console));
                }
            }
            
            console.tell(new ConsoleActor.Print("[CLI] Shutting down..."));
            Thread.sleep(100); // Give time for final message
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            system.terminate();
        }
    }

    
}
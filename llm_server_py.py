#!/usr/bin/env python3
"""
LLM Server for Akka Actor System
Author: Based on Abhinav Gangadharan's notebook
"""

import sys
import json
import argparse
import logging
from transformers import AutoTokenizer, AutoModelForCausalLM
import torch
import warnings

# Suppress transformer warnings
warnings.filterwarnings("ignore")

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[logging.FileHandler('llm_server.log'), logging.StreamHandler(sys.stderr)]
)

class LLMServer:
    def __init__(self, model_id="Qwen/Qwen2.5-3B-Instruct", use_cache=True):
        self.model_id = model_id
        self.use_cache = use_cache
        self.model = None
        self.tokenizer = None
        self.device = None
        self.logger = logging.getLogger(__name__)
        
    def load_model(self):
        """Load the model and tokenizer"""
        try:
            self.logger.info(f"Loading model: {self.model_id}")
            
            # Check for GPU availability
            if torch.cuda.is_available():
                self.device = "cuda"
                self.logger.info("Using GPU")
            else:
                self.device = "cpu"
                self.logger.info("Using CPU")
            
            # Load tokenizer
            self.tokenizer = AutoTokenizer.from_pretrained(
                self.model_id, 
                local_files_only=self.use_cache
            )
            
            # Load model
            self.model = AutoModelForCausalLM.from_pretrained(
                self.model_id,
                torch_dtype="auto" if self.device == "cuda" else torch.float32,
                device_map="auto" if self.device == "cuda" else None,
                local_files_only=self.use_cache,
            )
            
            if self.device == "cpu":
                self.model = self.model.to(self.device)
            
            self.logger.info("Model loaded successfully")
            return True
            
        except Exception as e:
            self.logger.error(f"Failed to load model: {e}")
            return False
    
    def generate(self, prompt, target_lang=None, history=None, max_new_tokens=128, temperature=0.7):
        """Generate response from the model"""
        try:
            # Build the conversation
            messages = []
            
            # Add system message for translation if target language specified
            if target_lang:
                system_content = f"You are a helpful assistant that translates text to {target_lang}. Continue the conversation without explanation."
                messages.append({"role": "system", "content": system_content})
            else:
                messages.append({"role": "system", "content": "You are Qwen, created by Alibaba Cloud. You are a helpful assistant."})
            
            # Add history if provided
            if history:
                for i, msg in enumerate(history):
                    role = "user" if i % 2 == 0 else "assistant"
                    messages.append({"role": role, "content": msg})
            
            # Add current prompt
            messages.append({"role": "user", "content": prompt})
            
            # Apply chat template
            text = self.tokenizer.apply_chat_template(
                messages, 
                tokenize=False, 
                add_generation_prompt=True
            )
            
            # Tokenize
            inputs = self.tokenizer(text, return_tensors="pt").to(self.model.device)
            
            # Generate
            with torch.no_grad():
                outputs = self.model.generate(
                    **inputs,
                    max_new_tokens=max_new_tokens,
                    temperature=temperature,
                    do_sample=True if temperature > 0 else False,
                    pad_token_id=self.tokenizer.eos_token_id
                )
            
            # Decode
            response = self.tokenizer.decode(outputs[0], skip_special_tokens=True)
            
            # Extract only the assistant's response
            # Split by the last "assistant" marker
            if "assistant\n" in response:
                response = response.split("assistant\n")[-1].strip()
            elif "assistant" in response:
                response = response.split("assistant")[-1].strip()
            
            return response
            
        except Exception as e:
            self.logger.error(f"Generation failed: {e}")
            raise

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="Qwen/Qwen2.5-3B-Instruct", help="Model ID")
    parser.add_argument("--mode", default="interactive", help="Mode of operation")
    parser.add_argument("--use-cache", action="store_true", default=True, help="Use cached model files")
    parser.add_argument("--download-first", action="store_true", help="Download model files first")
    args = parser.parse_args()
    
    # Download model files if requested
    if args.download_first:
        from huggingface_hub import hf_hub_download, login
        
        # You should set this as environment variable instead
        token = os.environ.get("HUGGING_FACE_API_KEY")
        if token:
            login(token=token)
        
        filenames = [
            "config.json",
            "generation_config.json",
            "model.safetensors.index.json",
            "model-00001-of-00002.safetensors",
            "model-00002-of-00002.safetensors",
            "tokenizer.json",
            "tokenizer_config.json",
            "vocab.json",
            "merges.txt",
        ]
        
        for filename in filenames:
            hf_hub_download(repo_id=args.model, filename=filename)
        
        print("Model files downloaded", file=sys.stderr)
    
    # Initialize server
    server = LLMServer(model_id=args.model, use_cache=args.use_cache)
    
    # Signal ready (before loading model for quick response)
    print("READY", flush=True)
    sys.stdout.flush()
    
    # Process commands
    for line in sys.stdin:
        try:
            line = line.strip()
            if not line:
                continue
                
            request = json.loads(line)
            command = request.get("command")
            
            if command == "load_model":
                success = server.load_model()
                response = {"status": "loaded" if success else "failed"}
                print(json.dumps(response), flush=True)
                sys.stdout.flush()
                
            elif command == "generate":
                if server.model is None:
                    print(json.dumps({"error": "Model not loaded"}), flush=True)
                    sys.stdout.flush()
                    continue
                
                prompt = request.get("prompt", "")
                target_lang = request.get("target_lang")
                history = request.get("history", [])
                max_tokens = request.get("max_tokens", 128)
                temperature = request.get("temperature", 0.7)
                
                text = server.generate(
                    prompt=prompt,
                    target_lang=target_lang,
                    history=history,
                    max_new_tokens=max_tokens,
                    temperature=temperature
                )
                
                response = {"text": text, "status": "success"}
                print(json.dumps(response), flush=True)
                sys.stdout.flush()
                
            elif command == "test":
                # Quick test without full generation
                response = {"status": "ok", "model_loaded": server.model is not None}
                print(json.dumps(response), flush=True)
                sys.stdout.flush()
                
            elif command == "shutdown":
                logging.info("Shutdown command received")
                break
                
            else:
                response = {"error": f"Unknown command: {command}"}
                print(json.dumps(response), flush=True)
                sys.stdout.flush()
                
        except json.JSONDecodeError as e:
            response = {"error": f"Invalid JSON: {e}"}
            print(json.dumps(response), flush=True)
            sys.stdout.flush()
            
        except Exception as e:
            logging.error(f"Error processing request: {e}")
            response = {"error": str(e)}
            print(json.dumps(response), flush=True)
            sys.stdout.flush()
    
    logging.info("Server shutting down")

if __name__ == "__main__":
    import os
    main()

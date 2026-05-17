# syntax=docker/dockerfile:1.7
FROM ollama/ollama

# Default to a CPU-friendly model. Override via OLLAMA_MODEL env at runtime.
ENV OLLAMA_MODEL=qwen2.5:3b

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 11434

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]

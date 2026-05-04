let socket;
let currentBotBubble = null;  // tracks the active streaming bubble

function initChat() {
    const protocol = location.protocol === "https:" ? "wss://" : "ws://";
    socket = new WebSocket(protocol + location.host + "/procurement/chat");

    socket.onopen = () => updateStatus("Connected");
    socket.onclose = () => { updateStatus("Disconnected"); hideLoader(); };
    socket.onerror = () => { updateStatus("Error"); hideLoader(); };

    socket.onmessage = (event) => {
        const chunk = event.data;

        if (chunk === "[DONE]") {
            // streaming finished - finalize the bubble
            currentBotBubble = null;
            hideLoader();
            scrollToBottom();
            return;
        }

        if (chunk.startsWith("[ERROR]")) {
            currentBotBubble = null;
            hideLoader();
            appendMessage("bot", chunk);
            return;
        }

        if (!currentBotBubble) {
            // first chunk - create the bot bubble and accumulate raw text in a data attr
            const chatBox = document.getElementById("chat-box");

            const timestamp = document.createElement("div");
            timestamp.className = "timestamp";
            timestamp.innerText = new Date().toLocaleTimeString([], {
                hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
            });

            const wrapper = document.createElement("div");
            wrapper.className = "message bot";

            const bubble = document.createElement("div");
            bubble.className = "bubble";
            bubble.dataset.raw = "";  // accumulate raw markdown here

            wrapper.appendChild(bubble);
            chatBox.appendChild(timestamp);
            chatBox.appendChild(wrapper);

            currentBotBubble = bubble;
        }

        // Accumulate raw markdown, render as HTML
        currentBotBubble.dataset.raw += chunk;
        currentBotBubble.innerHTML = renderMarkdown(currentBotBubble.dataset.raw);
        scrollToBottom();
    };

    document.getElementById("messageInput")
        .addEventListener("keydown", function (e) {
            if (e.key === "Enter" && e.ctrlKey) {
                e.preventDefault();
                sendMessage();
            }
        });
}

function sendMessage() {
    const input = document.getElementById("messageInput");
    const sendButton = document.querySelector(".chat-form button");
    const text = input.value.trim();

    if (!text || socket.readyState !== WebSocket.OPEN || sendButton.disabled) return;

    appendMessage("user", text);
    currentBotBubble = null;  // Reset to ensure new bot response creates a new bubble
    showLoader();
    socket.send(text);
    input.value = "";
}

function appendMessage(role, content) {
    const chatBox = document.getElementById("chat-box");

    const timestamp = document.createElement("div");
    timestamp.className = "timestamp";
    timestamp.innerText = new Date().toLocaleTimeString([], {
        hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    });

    const wrapper = document.createElement("div");
    wrapper.className = "message " + role;

    const bubble = document.createElement("div");
    bubble.className = "bubble";

    if (role === "bot") {
        bubble.innerHTML = renderMarkdown(content);
    } else {
        bubble.innerText = content;
    }

    wrapper.appendChild(bubble);
    chatBox.appendChild(timestamp);
    chatBox.appendChild(wrapper);
    scrollToBottom();
}

// Minimal markdown renderer - handles what your Ollama responses actually produce
function renderMarkdown(text) {
    return text
        // **bold**
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        // numbered list: "1. item" → <ol><li>
        .replace(/^(\d+)\.\s+(.+)$/gm, '<li>$2</li>')
        // wrap consecutive <li> blocks in <ol>
        .replace(/(<li>.*<\/li>(\n|$))+/gs, (match) => `<ol>${match}</ol>`)
        // line breaks
        .replace(/\n/g, '<br>');
}

function updateStatus(text) {
    const status = document.getElementById("status");
    if (status) status.innerText = text;
}

function showLoader() {
    const loader = document.getElementById("loader");
    const sendButton = document.querySelector(".chat-form button");
    if (loader) loader.style.display = "flex";
    if (sendButton) { sendButton.disabled = true; sendButton.style.display = "none"; }
}

function hideLoader() {
    const loader = document.getElementById("loader");
    const sendButton = document.querySelector(".chat-form button");
    if (loader) loader.style.display = "none";
    if (sendButton) { sendButton.disabled = false; sendButton.style.display = "block"; }
}

function scrollToBottom() {
    const chatBox = document.getElementById("chat-box");
    chatBox.scrollTop = chatBox.scrollHeight;
}

window.onload = initChat;

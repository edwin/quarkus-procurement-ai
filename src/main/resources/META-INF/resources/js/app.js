let socket;

function initChat() {
    const protocol = location.protocol === "https:" ? "wss://" : "ws://";
    socket = new WebSocket(protocol + location.host + "/procurement/chat");

    socket.onopen = () => {
        updateStatus("Connected");
    };

    socket.onclose = () => {
        updateStatus("Disconnected");
        hideLoader();
    };

    socket.onerror = () => {
        updateStatus("Error");
        hideLoader();
    };

    socket.onmessage = (event) => {
        hideLoader();
        appendMessage("bot", event.data);
    };

    // Ctrl+Enter to send, Enter for new line
    document.getElementById("messageInput")
        .addEventListener("keydown", function(e) {
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

    if (!text || socket.readyState !== WebSocket.OPEN || sendButton.disabled) {
        return;
    }

    // show user message immediately
    appendMessage("user", text);

    // show loader while waiting for response
    showLoader();

    socket.send(text);
    input.value = "";
}

function appendMessage(role, content) {
    const chatBox = document.getElementById("chat-box");

    // Create timestamp
    const timestamp = document.createElement("div");
    timestamp.className = "timestamp";
    const now = new Date();
    const timeString = now.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit', second:'2-digit'});
    timestamp.innerText = timeString;

    const wrapper = document.createElement("div");
    wrapper.className = "message " + role;

    const bubble = document.createElement("div");
    bubble.className = "bubble";
    bubble.innerText = content;

    wrapper.appendChild(bubble);
    chatBox.appendChild(timestamp);
    chatBox.appendChild(wrapper);

    chatBox.scrollTop = chatBox.scrollHeight;
}

function updateStatus(text) {
    const status = document.getElementById("status");
    if (status) {
        status.innerText = text;
    }
}

function showLoader() {
    const loader = document.getElementById("loader");
    const sendButton = document.querySelector(".chat-form button");
    if (loader) {
        loader.style.display = "flex";
    }
    if (sendButton) {
        sendButton.disabled = true;
        sendButton.style.display = "none";
    }
}

function hideLoader() {
    const loader = document.getElementById("loader");
    const sendButton = document.querySelector(".chat-form button");
    if (loader) {
        loader.style.display = "none";
    }
    if (sendButton) {
        sendButton.disabled = false;
        sendButton.style.display = "block";
    }
}

// init on page load
window.onload = initChat;

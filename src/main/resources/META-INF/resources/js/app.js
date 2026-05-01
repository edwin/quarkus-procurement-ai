let socket;

function initChat() {
    const protocol = location.protocol === "https:" ? "wss://" : "ws://";
    socket = new WebSocket(protocol + location.host + "/procurement/chat");

    socket.onopen = () => {
        updateStatus("Connected");
    };

    socket.onclose = () => {
        updateStatus("Disconnected");
    };

    socket.onerror = () => {
        updateStatus("Error");
    };

    socket.onmessage = (event) => {
        appendMessage("bot", event.data);
    };

    // Enter to send
    document.getElementById("messageInput")
        .addEventListener("keypress", function(e) {
            if (e.key === "Enter") {
                e.preventDefault();
                sendMessage();
            }
        });
}

function sendMessage() {
    const input = document.getElementById("messageInput");
    const text = input.value.trim();

    if (!text || socket.readyState !== WebSocket.OPEN) {
        return;
    }

    // show user message immediately
    appendMessage("user", text);

    socket.send(text);
    input.value = "";
}

function appendMessage(role, content) {
    const chatBox = document.getElementById("chat-box");

    const wrapper = document.createElement("div");
    wrapper.className = "message " + role;

    const bubble = document.createElement("div");
    bubble.className = "bubble";
    bubble.innerText = content;

    wrapper.appendChild(bubble);
    chatBox.appendChild(wrapper);

    chatBox.scrollTop = chatBox.scrollHeight;
}

function updateStatus(text) {
    const status = document.getElementById("status");
    if (status) {
        status.innerText = text;
    }
}

// init on page load
window.onload = initChat;
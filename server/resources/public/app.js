function toggleWidth(columnId) {
    const element = document.getElementById(columnId);
    element.classList.toggle('narrow');
  }

document.addEventListener("DOMContentLoaded", (event) => {
  document
    .getElementById("connectButton")
    .addEventListener("click", function () {
      let port = document.getElementById("port").value;
      let ws_url = "ws://localhost:7777/connect-ws";
      let socket;

      try {
        socket = new WebSocket(ws_url);
      } catch (error) {
        console.error("WebSocket initialization error:", error);
        return;
      }

      socket.addEventListener("open", function (event) {
        console.log("WebSocket is connected.");

        let nrepl_url = "http://localhost:7777/connect-nrepl/" + port;
        fetch(nrepl_url)
          .then((response) => response.text())
          .then((data) => console.log(data))
          .catch((error) => console.error("Error:", error));
      });

      socket.addEventListener("message", function (event) {
        console.log("Message from server:", event.data);
      });

      socket.addEventListener("error", function (event) {
        console.error("WebSocket error:", event);
      });

      socket.addEventListener("close", function (event) {
        console.log("WebSocket connection closed:", event);
      });

      window.addEventListener("beforeunload", function () {
        if (socket) {
          socket.close();
        }
      });
    });
});

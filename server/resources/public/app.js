
function toggleContent(columnNumber) {
  const column = document.getElementById('column' + columnNumber);
  const content = column.querySelector('.column-content');
  const button = column.querySelector('.button');
  const sideText = column.querySelector('.side-text');
  const header = column.querySelector('.column-header');

  if (column.classList.contains('minimized')) {
    column.classList.remove('minimized');
    content.classList.remove('hidden');
    sideText.style.display = 'none';
    button.classList.remove('hidden');
    header.style.display = 'flex';
    button.innerHTML = '<i class="fas fa-compress-alt"></i>';
  } else {
    column.classList.add('minimized');
    content.classList.add('hidden');
    sideText.style.display = 'block';
    button.classList.add('hidden');
    header.style.display = 'none';
  }
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

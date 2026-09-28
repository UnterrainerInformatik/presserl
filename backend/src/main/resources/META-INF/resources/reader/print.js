// Print views: the "Print" button is hidden until this script shows it and binds the browser's print
// dialog. Served from the reader's own origin, so the content security policy stays 'self'.
document.querySelectorAll("[data-print]").forEach(function (button) {
  button.hidden = false;
  button.addEventListener("click", function () {
    window.print();
  });
});

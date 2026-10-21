// Tema claro/oscuro: respeta el del sistema y recuerda la elección (si el navegador lo permite).
(function () {
  "use strict";
  var raiz = document.documentElement;
  var oscuroSistema = window.matchMedia("(prefers-color-scheme: dark)");

  function temaActual() {
    var t = raiz.getAttribute("data-theme");
    if (t === "light" || t === "dark") return t;
    return oscuroSistema.matches ? "dark" : "light";
  }

  document.addEventListener("DOMContentLoaded", function () {
    var boton = document.querySelector(".tema-boton");
    if (!boton) return;
    var actualizar = function () {
      boton.setAttribute("aria-label", temaActual() === "dark" ? "Cambiar a modo claro" : "Cambiar a modo oscuro");
    };
    actualizar();
    boton.addEventListener("click", function () {
      var nuevo = temaActual() === "dark" ? "light" : "dark";
      raiz.setAttribute("data-theme", nuevo);
      try {
        localStorage.setItem("tema", nuevo);
      } catch (e) {
        /* sin almacenamiento: el tema dura hasta recargar */
      }
      actualizar();
    });
    oscuroSistema.addEventListener("change", actualizar);
  });
})();

"use strict";

document.addEventListener("submit", (event) => {
  const form = event.target.closest("form[data-confirm]");
  if (form && !window.confirm(form.dataset.confirm || "确定继续吗？")) {
    event.preventDefault();
  }
});

document.addEventListener("click", (event) => {
  const target = event.target.closest("[data-copy-value]");
  if (target) {
    navigator.clipboard.writeText(target.dataset.copyValue || "");
  }
});

document.addEventListener("keydown", (event) => {
  const target = event.target.closest("[data-copy-value]");
  if (target && (event.key === "Enter" || event.key === " ")) {
    event.preventDefault();
    navigator.clipboard.writeText(target.dataset.copyValue || "");
  }
});

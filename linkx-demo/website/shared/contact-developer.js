(function () {
  const QQ = "743258831";

  function renderContact(mount) {
    const base = mount.dataset.assetsBase || "assets/";
    const prefix = mount.dataset.contactPrefix || "";
    mount.innerHTML =
      prefix +
      '<span class="contact-qq-wrap">' +
      '<button type="button" class="contact-qq__trigger" aria-label="联系开发者 QQ ' +
      QQ +
      '" aria-expanded="false">' +
      QQ +
      "</button>" +
      '<span class="contact-qq__qrcode" role="tooltip" aria-hidden="true">' +
      '<img src="' +
      base +
      'qq-qrcode.png" alt="扫一扫加我为好友" width="200" height="200" />' +
      "</span>" +
      "</span>";
  }

  document.querySelectorAll("[data-contact-developer]").forEach(renderContact);

  document.querySelectorAll(".contact-qq-wrap").forEach(function (wrap) {
    var trigger = wrap.querySelector(".contact-qq__trigger");
    if (!trigger) return;

    trigger.addEventListener("click", function (e) {
      e.preventDefault();
      e.stopPropagation();
      var open = !wrap.classList.contains("is-open");
      document.querySelectorAll(".contact-qq-wrap.is-open").forEach(function (other) {
        if (other !== wrap) {
          other.classList.remove("is-open");
          var otherTrigger = other.querySelector(".contact-qq__trigger");
          if (otherTrigger) otherTrigger.setAttribute("aria-expanded", "false");
        }
      });
      wrap.classList.toggle("is-open", open);
      trigger.setAttribute("aria-expanded", open ? "true" : "false");
    });
  });

  document.addEventListener("click", function (e) {
    if (e.target.closest(".contact-qq-wrap")) return;
    document.querySelectorAll(".contact-qq-wrap.is-open").forEach(function (wrap) {
      wrap.classList.remove("is-open");
      var trigger = wrap.querySelector(".contact-qq__trigger");
      if (trigger) trigger.setAttribute("aria-expanded", "false");
    });
  });
})();

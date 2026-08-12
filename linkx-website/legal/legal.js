(function () {
  const links = Array.from(document.querySelectorAll(".docs-sidebar__link"));
  const sections = links
    .map((link) => {
      const id = link.getAttribute("href")?.slice(1);
      return id ? document.getElementById(id) : null;
    })
    .filter(Boolean);

  function setActiveLink(id) {
    links.forEach((link) => {
      link.classList.toggle("is-active", link.getAttribute("href") === `#${id}`);
    });
  }

  function onScroll() {
    const offset = 120;
    let current = sections[0]?.id;
    for (const section of sections) {
      if (section.getBoundingClientRect().top - offset <= 0) {
        current = section.id;
      }
    }
    if (current) setActiveLink(current);
  }

  if (sections.length) {
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
  }
})();

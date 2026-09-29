(() => {
  const documentRoot = document.querySelector('.document');
  const firstPage = documentRoot.querySelector('.page-one');
  const firstSection = firstPage.querySelector('.first-project-section');
  const firstSlot = firstPage.querySelector('.first-project-slot');
  const template = document.getElementById('continuation-template');
  const source = document.getElementById('project-source');
  const projects = Array.from(source.querySelectorAll('.project-card'));
  source.remove();

  const bottom = page => page.getBoundingClientRect().bottom - 58;
  const fits = (project, page) => project.getBoundingClientRect().bottom <= bottom(page) - 2;
  const fail = (code, index) => {
    document.documentElement.dataset.cvError = code;
    document.documentElement.dataset.cvProjectIndex = String(index);
  };

  // The sidebar is independent; the summary, skills and projects flow down the right column.
  const sidebar = firstPage.querySelector('.support-column');
  const summary = firstPage.querySelector('.technical-summary-block');
  if ((summary && summary.getBoundingClientRect().bottom > bottom(firstPage))
      || sidebar.getBoundingClientRect().bottom > bottom(firstPage)) {
    fail('FIRST_PAGE_OVERFLOW', -1);
    return;
  }

  const expertise = firstPage.querySelector('.main-section:not(.first-project-section)');
  if (expertise) {
    const chips = Array.from(expertise.querySelectorAll('.skill-chip'));
    const firstOverflow = chips.findIndex(chip => chip.getBoundingClientRect().bottom > bottom(firstPage) - 2);
    if (firstOverflow !== -1) chips.slice(firstOverflow).forEach(chip => chip.remove());
    expertise.querySelectorAll('.skills-group').forEach(group => {
      if (!group.querySelector('.skill-chip')) group.remove();
    });
    if (!expertise.querySelector('.skill-chip')) expertise.remove();
  }

  let next = 0;
  for (let index = 0; index < Math.min(projects.length, 2); index++) {
    firstSection.style.display = 'block';
    firstSlot.append(projects[index]);
    if (fits(projects[index], firstPage)) next = index + 1;
    else {
      projects[index].remove();
      if (!next) firstSection.style.display = 'none';
      break;
    }
  }

  let page = null;
  let slot = null;
  for (let index = next; index < projects.length; index++) {
    const project = projects[index];
    if (!page) {
      page = template.content.firstElementChild.cloneNode(true);
      page.setAttribute('aria-label', `CV page ${documentRoot.querySelectorAll('.cv-page').length + 1}`);
      documentRoot.append(page);
      slot = page.querySelector('.continuation-projects');
    }
    slot.append(project);
    if (fits(project, page)) continue;

    project.remove();
    if (slot.children.length === 0) {
      fail('PROJECT_TOO_LARGE', index);
      return;
    }
    page = template.content.firstElementChild.cloneNode(true);
    page.setAttribute('aria-label', `CV page ${documentRoot.querySelectorAll('.cv-page').length + 1}`);
    documentRoot.append(page);
    slot = page.querySelector('.continuation-projects');
    slot.append(project);
    if (!fits(project, page)) {
      fail('PROJECT_TOO_LARGE', index);
      return;
    }
  }

  const pages = documentRoot.querySelectorAll('.cv-page');
  pages.forEach((item, index) => {
    item.querySelector('.page-number').textContent = `Page ${index + 1} / ${pages.length}`;
  });
  document.documentElement.dataset.cvPages = String(pages.length);
  document.documentElement.dataset.cvError = '';
})();

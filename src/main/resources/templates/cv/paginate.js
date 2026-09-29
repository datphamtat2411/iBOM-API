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

  // The reference page reserves this region for identity/summary. Never print over it.
  const summary = firstPage.querySelector('.technical-summary-block');
  const grid = firstPage.querySelector('.content-grid');
  if ((summary && summary.getBoundingClientRect().bottom > grid.getBoundingClientRect().top - 8)
      || Array.from(grid.children).some(column => column.getBoundingClientRect().bottom > bottom(firstPage) + 2)) {
    fail('FIRST_PAGE_OVERFLOW', -1);
    return;
  }

  let next = 0;
  if (projects.length) {
    firstSection.style.display = 'block';
    firstSlot.append(projects[0]);
    if (fits(projects[0], firstPage)) {
      next = 1;
    } else {
      projects[0].remove();
      firstSection.style.display = 'none';
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

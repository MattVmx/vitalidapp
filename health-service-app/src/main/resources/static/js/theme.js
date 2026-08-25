(function () {
  'use strict';

  if (window.vitalidappTheme) {
    return;
  }

  var storageKey = 'vitalidapp-theme';
  var root = document.documentElement;
  var systemPreference = window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null;

  function getStoredTheme() {
    try {
      var theme = window.localStorage.getItem(storageKey);
      return theme === 'dark' || theme === 'light' ? theme : null;
    } catch (error) {
      return null;
    }
  }

  function getPreferredTheme() {
    return systemPreference && systemPreference.matches ? 'dark' : 'light';
  }

  function updateControls(theme) {
    var isDark = theme === 'dark';
    var actionLabel = isDark ? 'Activar tema claro' : 'Activar tema oscuro';

    document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
      button.setAttribute('aria-label', actionLabel);
      button.setAttribute('aria-pressed', String(isDark));
      button.setAttribute('title', actionLabel);

      var label = button.querySelector('[data-theme-label]');
      if (label) {
        label.textContent = actionLabel;
      }
    });
  }

  function applyTheme(theme, persist) {
    root.setAttribute('data-theme', theme);
    root.style.colorScheme = theme;

    if (persist) {
      try {
        window.localStorage.setItem(storageKey, theme);
      } catch (error) {
        // El tema sigue funcionando aunque el navegador bloquee el almacenamiento.
      }
    }

    updateControls(theme);
  }

  function toggleTheme() {
    var currentTheme = root.getAttribute('data-theme') || getPreferredTheme();
    applyTheme(currentTheme === 'dark' ? 'light' : 'dark', true);
  }

  function initializeControls() {
    updateControls(root.getAttribute('data-theme') || getPreferredTheme());

    document.addEventListener('click', function (event) {
      var button = event.target.closest('[data-theme-toggle]');
      if (button) {
        toggleTheme();
      }
    });
  }

  applyTheme(getStoredTheme() || getPreferredTheme(), false);

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeControls);
  } else {
    initializeControls();
  }

  if (systemPreference && systemPreference.addEventListener) {
    systemPreference.addEventListener('change', function (event) {
      if (!getStoredTheme()) {
        applyTheme(event.matches ? 'dark' : 'light', false);
      }
    });
  }

  window.vitalidappTheme = {
    set: function (theme) {
      if (theme === 'dark' || theme === 'light') {
        applyTheme(theme, true);
      }
    }
  };
})();

/*
 * EroideWeb — ponte del gestore password.
 *
 * Gira in ogni frame, in un mondo JavaScript isolato quando la WebView lo
 * consente (la pagina non vede né questo codice né l'oggetto del ponte).
 * Non legge nulla da solo e non compila mai da solo: segnala soltanto
 *  - "focus": l'utente ha toccato un campo di accesso (il browser mostra
 *    "Accedi come …" se ha credenziali per questa origine);
 *  - "submit": un accesso è stato inviato (il browser chiede se salvare).
 * Compila solo quando il browser lo chiede, dopo un tocco dell'utente.
 * L'origine dei messaggi la stabilisce il browser, non questo script.
 */
(function () {
  'use strict';
  var bridge = window.EroidePasswords;
  if (!bridge) return;

  var lastField = null;
  var lastSent = '';
  var CHECK_DELAY_MS = 1500;

  function isInput(el) { return !!el && el.tagName === 'INPUT'; }
  function isPassword(el) { return isInput(el) && (el.type || '').toLowerCase() === 'password'; }
  function isUserField(el) {
    if (!isInput(el)) return false;
    var type = (el.type || 'text').toLowerCase();
    return type === 'text' || type === 'email' || type === 'tel';
  }
  function visible(el) {
    return !!el && el.isConnected && !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
  }
  function scopeOf(el) {
    if (!el) return document;
    if (el.form) return el.form;
    return (el.closest && el.closest('form')) || document;
  }

  /** Primo campo password visibile e il campo utente che lo precede. */
  function fields(scope) {
    var inputs = Array.prototype.slice.call(scope.querySelectorAll('input'));
    var password = null;
    for (var i = 0; i < inputs.length; i++) {
      if (isPassword(inputs[i]) && visible(inputs[i])) { password = inputs[i]; break; }
    }
    var user = null;
    var limit = password ? inputs.indexOf(password) : inputs.length;
    for (var j = limit - 1; j >= 0; j--) {
      if (isUserField(inputs[j]) && visible(inputs[j])) { user = inputs[j]; break; }
    }
    return { user: user, password: password };
  }

  /** Campo password, oppure campo utente di un modulo di accesso (anche in due passi). */
  function looksLikeLogin(el) {
    if (isPassword(el)) return true;
    if (!isUserField(el)) return false;
    var found = fields(scopeOf(el));
    if (found.password) return found.user === el;
    var autocomplete = (el.getAttribute('autocomplete') || '').toLowerCase();
    return autocomplete.indexOf('username') !== -1 || (el.type || '').toLowerCase() === 'email';
  }

  function post(message) {
    try { bridge.postMessage(JSON.stringify(message)); } catch (e) { /* ponte chiuso */ }
  }

  function snapshot(scope) {
    var found = fields(scope);
    if (!found.password || !found.password.value) return null;
    return {
      field: found.password,
      username: found.user ? found.user.value : '',
      password: found.password.value
    };
  }

  function send(data) {
    if (!data) return;
    var key = data.username + '\u0000' + data.password;
    if (key === lastSent) return;
    lastSent = key;
    post({ type: 'submit', username: data.username, password: data.password });
  }

  /**
   * Accesso senza invio del modulo (app a pagina singola): si propone il
   * salvataggio solo se poco dopo il campo password sparisce, cioè se
   * l'accesso è andato a buon fine. Così il pulsante "mostra password" o un
   * errore di battitura non fanno comparire la richiesta.
   */
  function captureIfLoginSucceeds(scope) {
    var data = snapshot(scope);
    if (!data) return;
    setTimeout(function () {
      if (!visible(data.field)) send(data);
    }, CHECK_DELAY_MS);
  }

  document.addEventListener('focusin', function (event) {
    var el = event.target;
    if (!looksLikeLogin(el)) return;
    lastField = el;
    post({ type: 'focus' });
  }, true);

  document.addEventListener('submit', function (event) {
    send(snapshot(event.target));
  }, true);

  document.addEventListener('click', function (event) {
    var target = event.target;
    var button = target && target.closest &&
      target.closest('button, input[type="submit"], input[type="button"], [role="button"]');
    if (button) captureIfLoginSucceeds(scopeOf(button));
  }, true);

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Enter' && isPassword(event.target)) captureIfLoginSucceeds(scopeOf(event.target));
  }, true);

  // Navigazione verso un'altra pagina subito dopo l'accesso.
  window.addEventListener('pagehide', function () {
    if (lastField) send(snapshot(scopeOf(lastField)));
  }, true);

  var setNativeValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;

  /** Valore impostato come se l'avesse scritto l'utente (React, Vue, Angular lo vedono). */
  function setField(el, value) {
    if (!el) return;
    setNativeValue.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  bridge.onmessage = function (event) {
    var message;
    try { message = JSON.parse(event.data); } catch (e) { return; }
    if (!message || message.type !== 'fill' || !lastField || !lastField.isConnected) return;
    var found = fields(scopeOf(lastField));
    if (found.password) {
      if (found.user && message.username) setField(found.user, message.username);
      setField(found.password, message.password || '');
      // Ciò che abbiamo appena compilato non va riproposto per il salvataggio.
      lastSent = (found.user ? message.username : '') + '\u0000' + (message.password || '');
    } else if (isUserField(lastField) && message.username) {
      // Primo passo di un accesso in due passi: solo l'utente.
      setField(lastField, message.username);
    }
  };
})();

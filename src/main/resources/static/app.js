// Small progressive enhancements. Every form still works without this file;
// it only adds copy buttons, select-all and delete confirmations.
(function () {
    'use strict';

    // --- copy a short link to the clipboard ---------------------------------------
    document.addEventListener('click', async function (event) {
        const button = event.target.closest('[data-copy]');
        if (!button) {
            return;
        }
        const text = button.dataset.copy;
        const label = button.textContent;
        try {
            await navigator.clipboard.writeText(text);
            button.textContent = 'Copied!';
        } catch (e) {
            // clipboard API is unavailable outside a secure context (plain http)
            window.prompt('Copy this link:', text);
            return;
        }
        setTimeout(function () { button.textContent = label; }, 1500);
    });

    // --- confirm before destructive submits ---------------------------------------
    document.addEventListener('click', function (event) {
        const button = event.target.closest('[data-confirm]');
        if (button && !window.confirm(button.dataset.confirm)) {
            event.preventDefault();
        }
    });

    // --- select all + live "Delete Selected (n)" ----------------------------------
    document.querySelectorAll('[data-bulk-form]').forEach(function (form) {
        const master = form.querySelector('[data-select-all]');
        const bulk = form.querySelector('[data-bulk-delete]');
        if (!master || !bulk) {
            return;
        }
        const rows = function () {
            return Array.from(form.querySelectorAll('[data-row-select]'));
        };
        const sync = function () {
            const boxes = rows();
            const checked = boxes.filter(function (box) { return box.checked; }).length;
            master.checked = checked > 0 && checked === boxes.length;
            master.indeterminate = checked > 0 && checked < boxes.length;
            bulk.disabled = checked === 0;
            bulk.textContent = checked > 0 ? 'Delete Selected (' + checked + ')' : 'Delete Selected';
        };
        master.addEventListener('change', function () {
            rows().forEach(function (box) { box.checked = master.checked; });
            sync();
        });
        form.addEventListener('change', function (event) {
            if (event.target.matches('[data-row-select]')) {
                sync();
            }
        });
        sync();
    });
})();

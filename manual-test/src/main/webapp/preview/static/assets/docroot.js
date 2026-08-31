/* Docroot script probe.

   Sets a flag the moment it runs -- that alone proves the launcher served and executed this
   file -- then stamps the placeholder in the page.

   The stamping has to wait. A ZK page's DOM is built client-side by the zkmx(...) bootstrap,
   which the launcher emits AFTER this <script> tag, so #docroot-js-probe does not exist yet
   when this file executes. zk.afterMount is ZK's own post-build hook; the poll is a fallback
   for the case where zk is not on the page. Running only on DOMContentLoaded, as an earlier
   version of this probe did, finds nothing and silently reports a false failure. */
(function () {
    window.__docrootJsRan = true;

    function stamp() {
        var el = document.getElementById('docroot-js-probe');
        if (!el) {
            return false;
        }
        el.textContent = 'docroot.js RAN -- static script was served';
        el.style.color = '#1b8a3a';
        el.style.fontWeight = 'bold';
        return true;
    }

    if (window.zk && typeof window.zk.afterMount === 'function') {
        window.zk.afterMount(stamp);
    }
    // Fallback / belt-and-braces: poll briefly for the element.
    var tries = 0;
    var timer = setInterval(function () {
        if (stamp() || ++tries > 40) {
            clearInterval(timer);
        }
    }, 100);
})();

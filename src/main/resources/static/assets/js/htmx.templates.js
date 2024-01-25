customElements.define("slot-", class InjectTemplateElement extends HTMLElement {
    connectedCallback() {
        const template = document.querySelector(this.attributes["inject"]?.value) ?? null;
        if (template === null) {
            console.warn("Could not find template for this!", this);
            return;
        }
        if (!(template instanceof HTMLTemplateElement)) {
            console.warn("Inject is not a template!", this);
            return;
        }
        this.innerHTML = "";
        this.append(...template.content.cloneNode(true).childNodes);
        htmx.process(this);
    }
});

window.addEventListener("click", (event) => {
    const template = document.querySelector(event.target.attributes["hx-template"]?.value);
    if (!(template instanceof HTMLTemplateElement)) {
        return;
    }
    const target = document.querySelector(event.target.attributes["hx-target"]?.value) ?? null;
    if (target === null) {
        return;
    }
    target.innerHTML = "";
    target.append(...template.content.cloneNode(true).childNodes);
    htmx.process(target);
});

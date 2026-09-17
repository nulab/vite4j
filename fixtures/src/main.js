import { label } from "./shared.js"
import "./main.css"
import logo from "./logo.svg"

document.title = label
document.querySelector("#app")?.setAttribute("data-logo", logo)
if (location.hash === "#a") import("./lazy.js").then((m) => m.render())
if (location.hash === "#b") import("./lazy2.js").then((m) => m.render())

document.addEventListener("DOMContentLoaded", async () => {
    const res = await fetch("/api");
    const job_list = await res.json();
    const list = document.getElementById("job_list");

    job_list.forEach(job => {
        list.insertAdjacentHTML("beforeend", `<li><a href="job.html?id=${job}">${job}</a></li>`);
    });
});

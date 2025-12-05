let data = null;
let opened = false;

document.addEventListener("DOMContentLoaded", () => {
    const btn = document.getElementById("score_expand_button");
    const expanded = document.getElementById("expanded_score");

    btn.addEventListener("click", async () => {
        if (opened) {
            expanded.classList.add("hidden");
            opened = false;
            return;
        }

        if (data) {
            expanded.classList.remove("hidden");
            opened = true;
            return;
        }

        expanded.innerHTML = "Loading...";
        expanded.classList.remove("hidden");

        const params = new URLSearchParams(window.location.search);
        const id = params.get("id");

        const res = await fetch(`/api/score/${id}`);
        data = await res.json();

        if (!data) {
            alert("Can't get score details");
            expanded.innerHTML = "";
            return;
        }

        expanded.innerHTML = "";

        const scoreExplanation = data.constraints.map(({ name, score, weight }) => ({
            name,
            score,
            weight
        }));

        scoreExplanation.forEach(score => {
            const div = document.createElement("div");
            div.className = "flex flex-row gap-10 py-2";
            div.innerHTML = `
                <h3 class="w-1/3 font-semibold">${score.name}</h3>
                <div class="w-1/3"><strong>Score:</strong> ${score.score}</div>
                <div class="w-1/3"><strong>Weight:</strong> ${score.weight}</div>
            `;
            expanded.appendChild(div);
        });

        opened = true;
    });
});

(function() {
    console.log("Moodle Automator script injected successfully.");
    function scanPage() {
        const queDiv = document.querySelector('div.que');
        if (!queDiv || queDiv.dataset.processed === "true") return;
        const qtextDiv = queDiv.querySelector('.qtext');
        if (!qtextDiv) return;
        const questionText = qtextDiv.innerText.trim();
        const answerDiv = queDiv.querySelector('.answer');
        if (!answerDiv) return;
        const inputs = answerDiv.querySelectorAll('input[type="radio"]');
        const choices = [];
        inputs.forEach(inp => {
            if (inp.value === "-1") return;
            const labelId = inp.getAttribute('aria-labelledby');
            let labelText = labelId ? document.getElementById(labelId)?.innerText.trim() : "";
            if (!labelText) {
                labelText = document.querySelector(`label[for="${inp.id}"]`)?.innerText.trim() || "";
            }
            choices.push({ id: inp.id, name: inp.name, value: inp.value, text: labelText });
        });
        if (choices.length > 0) {
            queDiv.dataset.processed = "true";
            if (window.MoodleBridge) {
                window.MoodleBridge.onQuestionScraped(questionText, JSON.stringify(choices));
            }
        }
    }
    window.selectAnswerAndNext = function(selectedValue) {
        const input = document.querySelector(`input[type="radio"][value="${selectedValue}"]`);
        if (input) {
            input.click();
            const delay = Math.floor(Math.random() * 2000) + 1500;
            setTimeout(() => {
                document.getElementById('mod_quiz-next-nav')?.click();
            }, delay);
        }
    };
    setTimeout(scanPage, 1500);
})();

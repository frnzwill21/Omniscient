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
            const labelText = document.querySelector(`label[for="${inp.id}"]`)?.innerText.trim() || "";
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
            setTimeout(() => {
                document.getElementById('mod_quiz-next-nav')?.click();
            }, 2000);
        }
    };
    setTimeout(scanPage, 1500);
})();

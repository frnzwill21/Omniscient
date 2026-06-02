(function() {
    console.log("Moodle Automator script injected successfully.");

    // Helper function to convert image URL to base64 using fetch (preserves cookies)
    function imageToBase64(imgUrl, callback) {
        if (!imgUrl) {
            callback(null, null);
            return;
        }
        
        console.log("Attempting to fetch image for base64 conversion:", imgUrl);
        fetch(imgUrl, { credentials: 'include' })
            .then(response => {
                if (!response.ok) throw new Error("Network response was not ok");
                return response.blob();
            })
            .then(blob => {
                const reader = new FileReader();
                reader.onloadend = function() {
                    const dataUrl = reader.result;
                    const commaIdx = dataUrl.indexOf(',');
                    if (commaIdx !== -1) {
                        const base64data = dataUrl.substring(commaIdx + 1);
                        const mimeType = blob.type || "image/png";
                        callback(base64data, mimeType);
                    } else {
                        callback(null, null);
                    }
                };
                reader.readAsDataURL(blob);
            })
            .catch(err => {
                console.error("Error loading image for base64:", err);
                callback(null, null);
            });
    }

    // Helper function to extract question and choices
    function scanPage(force = false) {
        // Moodle questions are usually in div.que
        const queDiv = document.querySelector('div.que');
        if (!queDiv) {
            console.log("No quiz question container found on this page.");
            return;
        }

        if (force === true) {
            console.log("Forcing scan: clearing processed state.");
            delete queDiv.dataset.processed;
        } else if (queDiv.dataset.processed === "true") {
            console.log("Question already processed.");
            return;
        }

        // Get question text
        const qtextDiv = queDiv.querySelector('.qtext');
        if (!qtextDiv) {
            console.log("No question text found inside container.");
            return;
        }
        const questionText = qtextDiv.innerText.trim();

        // Get options
        const answerDiv = queDiv.querySelector('.answer');
        if (!answerDiv) {
            console.log("No answer block found.");
            return;
        }

        const inputs = answerDiv.querySelectorAll('input[type="radio"]');
        const choices = [];

        inputs.forEach(inp => {
            const val = inp.value;
            // Ignore the "Clear my choice" radio button (usually value -1)
            if (val === "-1") return;

            const labelId = inp.getAttribute('aria-labelledby');
            let labelText = "";

            if (labelId) {
                const labelEl = document.getElementById(labelId);
                if (labelEl) {
                    labelText = labelEl.innerText.trim();
                }
            } else {
                // Fallback to standard <label for="...">
                const labelEl = document.querySelector(`label[for="${inp.id}"]`);
                if (labelEl) {
                    labelText = labelEl.innerText.trim();
                }
            }

            choices.push({
                id: inp.id,
                name: inp.name,
                value: val,
                text: labelText
            });
        });

        if (choices.length > 0) {
            // Mark as processed
            queDiv.dataset.processed = "true";
            
            console.log("Scraped question:", questionText);
            console.log("Scraped choices:", choices);

            // Check if there is an image inside the question text or answers
            const imgEl = qtextDiv.querySelector('img') || answerDiv.querySelector('img');
            if (imgEl && imgEl.src) {
                console.log("Found image in kuis:", imgEl.src);
                imageToBase64(imgEl.src, function(base64Data, mimeType) {
                    if (base64Data) {
                        console.log("Image successfully converted to base64. Mime:", mimeType);
                        if (window.MoodleBridge) {
                            window.MoodleBridge.onQuestionScrapedWithImage(questionText, JSON.stringify(choices), base64Data, mimeType);
                        }
                    } else {
                        console.log("Image conversion failed, sending text-only.");
                        if (window.MoodleBridge) {
                            window.MoodleBridge.onQuestionScraped(questionText, JSON.stringify(choices));
                        }
                    }
                });
            } else {
                // Text only
                if (window.MoodleBridge) {
                    window.MoodleBridge.onQuestionScraped(questionText, JSON.stringify(choices));
                } else {
                    console.error("MoodleBridge not found on window object.");
                }
            }
        }
    }

    // Expose selectAnswer function to Android
    window.selectAnswerAndNext = function(selectedValue) {
        console.log("Received command to select value:", selectedValue);
        const queDiv = document.querySelector('div.que');
        if (!queDiv) return;

        const answerDiv = queDiv.querySelector('.answer');
        if (!answerDiv) return;

        const input = answerDiv.querySelector(`input[type="radio"][value="${selectedValue}"]`);
        if (input) {
            console.log("Clicking radio option:", input.id);
            input.click();

            // Simulate human delay before clicking next
            const delay = Math.floor(Math.random() * 2000) + 1500; // 1.5 to 3.5 seconds
            console.log(`Waiting ${delay}ms before navigating next...`);
            
            setTimeout(() => {
                const nextButton = document.getElementById('mod_quiz-next-nav');
                if (nextButton) {
                    console.log("Clicking next button...");
                    nextButton.click();
                } else {
                    console.error("Next button not found!");
                }
            }, delay);
        } else {
            console.error("Could not find radio option with value:", selectedValue);
        }
    };

    // Expose startScan function to Android to trigger scan manually
    window.startScan = function() {
        console.log("startScan called from Android");
        scanPage(true);
    };

    // Run scan on load
    setTimeout(scanPage, 1500);

})();

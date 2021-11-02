const apiEndPoint1 = "http://localhost:5000/autocomplete";
const apiEndPoint2 = "http://localhost:5000/submit";
const apiEndPoint3 = "http://localhost:5000/clear";

const searchForm = document.getElementById("searchbox");
const queryElement = document.getElementById("query");
const historyElement = document.getElementById("history");
const buttonElement = document.getElementById("clear");

async function deepkomplete(query) {
  const body = { query };
  try {
    const { data: suggestions } = await axios.post(apiEndPoint1, body);
    return suggestions.suggestions;
  } catch (error) {
    return ["suggestion1", "suggestion2"];
  }
}

async function submitQuery(query) {
  const body = { query };
  try {
    const { data: history } = await axios.post(apiEndPoint2, body);
    return history.history;
  } catch (error) {
    return ["history1", "history2"];
  }
}

async function clearHistory() {
  try {
    const { data: history } = await axios.post(apiEndPoint3);
    return history.history;
  } catch (error) {
    return [
      "delete1",
      "delete2",
      "delete3",
      "delete4",
      "delete5",
      "delete6",
      "delete7",
      "delete8",
      "delete9",
      "delete0",
    ];
  }
}

function activate_deepkomplete(inputElement) {
  let currentFocus;

  inputElement.addEventListener("input", async function (e) {
    closeAllLists();
    if (!inputElement.value) return;

    currentFocus = -1;
    const suggestionList = document.createElement("DIV");
    suggestionList.setAttribute("id", inputElement.id + "autocomplete-list");
    suggestionList.setAttribute("class", "autocomplete-items");
    inputElement.parentNode.appendChild(suggestionList);

    const suggestions = await deepkomplete(inputElement.value);

    for (let i = 0; i < suggestions.length; i++) {
      const suggestion = document.createElement("DIV");
      suggestion.innerHTML = suggestions[i];
      suggestion.innerHTML +=
        "<input type='hidden' value='" + suggestions[i] + "'>";
      suggestion.addEventListener("click", function (e) {
        inputElement.value = suggestion.getElementsByTagName("input")[0].value;
        closeAllLists();
      });
      suggestionList.appendChild(suggestion);
    }
  });

  inputElement.addEventListener("keydown", function (e) {
    let suggestionList = document.getElementById(
      inputElement.id + "autocomplete-list"
    );
    if (suggestionList)
      suggestionList = suggestionList.getElementsByTagName("div");
    if (e.keyCode == 40) {
      currentFocus++;
      addActive(suggestionList);
    } else if (e.keyCode == 38) {
      currentFocus--;
      addActive(suggestionList);
    } else if (e.keyCode == 13) {
      if (currentFocus == -1 || !suggestionList || suggestionList.length == 0)
        return;
      e.preventDefault();
      if (currentFocus > -1 && suggestionList) {
        suggestionList[currentFocus].click();
      }
    }
  });

  function addActive(suggestionList) {
    if (!suggestionList) return;
    removeActive(suggestionList);
    if (currentFocus >= suggestionList.length) currentFocus = 0;
    if (currentFocus < 0) currentFocus = suggestionList.length - 1;
    suggestionList[currentFocus].classList.add("autocomplete-active");
  }

  function removeActive(suggestionList) {
    for (var i = 0; i < suggestionList.length; i++) {
      suggestionList[i].classList.remove("autocomplete-active");
    }
  }

  function closeAllLists(element) {
    var suggestionList = document.getElementsByClassName("autocomplete-items");
    for (var i = 0; i < suggestionList.length; i++) {
      if (element != suggestionList[i] && element != inputElement) {
        suggestionList[i].parentNode.removeChild(suggestionList[i]);
      }
    }
  }

  document.addEventListener("click", function (e) {
    closeAllLists(e.target);
  });
}

function activate_submission(formElement, inputElement, historyElement) {
  formElement.addEventListener("submit", async function (event) {
    event.preventDefault();
    if (inputElement.value == "") return;
    const history = await submitQuery(inputElement.value);
    historyElement.innerHTML = "";
    for (let i = 0; i < history.length; i++) {
      const li = document.createElement("li");
      const span = document.createElement("span");
      span.innerText = history[i];
      li.appendChild(span);
      historyElement.appendChild(li);
    }
    inputElement.value = "";
  });
}

activate_deepkomplete(queryElement);
activate_submission(searchForm, queryElement, historyElement);

buttonElement.addEventListener("click", async function (event) {
  event.preventDefault();
  const history = await clearHistory();

  historyElement.innerHTML = "";
  for (let i = 0; i < history.length; i++) {
    const li = document.createElement("li");
    const span = document.createElement("span");
    span.innerText = history[i];
    li.appendChild(span);
    historyElement.appendChild(li);
  }
});

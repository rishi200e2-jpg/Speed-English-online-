with open("app/src/main/java/com/example/ui/QuizViewModel.kt", "r") as f:
    content = f.read()

idx1 = content.find('}) {\n        viewModelScope.launch {\n            try {\n                Log.d("QuizViewModel", "deleteQuiz:')
if idx1 != -1:
    idx2 = content.find('fun deleteQuestion', idx1)
    if idx2 != -1:
        content = content[:idx1] + '}\n\n    ' + content[idx2:]

idx3 = content.find('}) {\n        viewModelScope.launch {\n            try {\n                Log.d("QuizViewModel", "deleteQuestion:')
if idx3 != -1:
    idx4 = content.find('fun restoreDefaultSeedData()', idx3)
    if idx4 != -1:
        content = content[:idx3] + '}\n\n    ' + content[idx4:]

with open("app/src/main/java/com/example/ui/QuizViewModel.kt", "w") as f:
    f.write(content)

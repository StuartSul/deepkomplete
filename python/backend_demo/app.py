from flask import Flask, jsonify, request
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

history = []

@app.route('/autocomplete', methods=['POST'])
def autocomplete():
  try:
    query = request.get_json()
    suggestions = ["test1", "test2"]
    query = query['query']
    suggestions.append(query)
  except:
    pass
  return {'suggestions': suggestions}

@app.route('/submit', methods=['POST'])
def submit():
  try:
    query = request.get_json()
    history.insert(0, query['query'])
    if len(history) > 10:
      history.pop(10)
  except:
    pass
  return {'history': history}

@app.route('/clear', methods=['POST'])
def clear():
  try:
    history.clear()
  except:
    pass
  return {'history': history}
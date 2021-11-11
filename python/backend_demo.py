from deepkomplete import DeepKomplete
from flask import Flask, jsonify, request
from flask_cors import CORS
import timeit

app = Flask(__name__)
CORS(app)

print('Loading DeepKomplete...')
dk = DeepKomplete()
print('Load complete')

history = []

@app.route('/autocomplete', methods=['POST'])
def autocomplete():
  try:
    time_start = timeit.default_timer()
    query = request.get_json()
    suggestions = list(
      dk.suggest(query=query['query'], history=history)
    )
    time_end = timeit.default_timer()
    print('Response Time:', time_end - time_start)
    return {'suggestions': suggestions}
  except:
    return {'suggestions': []}

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

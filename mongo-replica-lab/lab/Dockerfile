FROM python:3.12-slim
WORKDIR /lab
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY replica_lab.py .
ENTRYPOINT ["python", "replica_lab.py"]
CMD ["status"]

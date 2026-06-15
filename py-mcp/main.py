from server import mcp_server

if __name__ == "__main__":
    mcp_server.run(transport="http", host="127.0.0.1", port=7001)

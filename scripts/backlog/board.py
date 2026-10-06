#!/usr/bin/env python3
"""Move a story's card on the "Stay Focused · Phase 1" kanban board.

Usage:
  python3 scripts/backlog/board.py status <issue-number> "<Status>"   # Backlog | Ready | In progress | In review | Done
  python3 scripts/backlog/board.py ready                               # list Ready stories (what to pick next)

Needs the gh CLI with a token that has the `project` scope (gh auth refresh -s project).
"""

from __future__ import annotations

import json
import os
import subprocess
import sys

OWNER = os.environ.get("BACKLOG_OWNER", "dculussoftwares")
REPO = os.environ.get("BACKLOG_REPO", "stayfocus")
PROJECT_TITLE = os.environ.get("BACKLOG_PROJECT_TITLE", "Stay Focused · Phase 1")


def graphql(query: str, **variables):
    res = subprocess.run(["gh", "api", "graphql", "--input", "-"], input=json.dumps({"query": query, "variables": variables}),
                         text=True, capture_output=True)
    if res.returncode != 0:
        raise SystemExit(res.stderr.strip())
    data = json.loads(res.stdout)
    if data.get("errors"):
        raise SystemExit(json.dumps(data["errors"], indent=2))
    return data["data"]


def project() -> dict:
    data = graphql("""query($org:String!,$q:String!){organization(login:$org){projectsV2(first:20,query:$q){nodes{
        id title fields(first:50){nodes{... on ProjectV2SingleSelectField{id name options{id name}}}}}}}}""",
                   org=OWNER, q=PROJECT_TITLE)
    for p in data["organization"]["projectsV2"]["nodes"]:
        if p["title"] == PROJECT_TITLE:
            return p
    raise SystemExit(f"project {PROJECT_TITLE!r} not found; has the backlog workflow run?")


def item_for_issue(project_id: str, number: int) -> str:
    data = graphql("""query($o:String!,$r:String!,$n:Int!){repository(owner:$o,name:$r){issue(number:$n){
        projectItems(first:20){nodes{id project{id}}}}}}""", o=OWNER, r=REPO, n=number)
    for item in data["repository"]["issue"]["projectItems"]["nodes"]:
        if item["project"]["id"] == project_id:
            return item["id"]
    raise SystemExit(f"issue #{number} is not on the board yet; run the backlog workflow")


def set_status(number: int, status: str) -> None:
    p = project()
    field = next(f for f in p["fields"]["nodes"] if f and f.get("name") == "Status")
    option = next((o for o in field["options"] if o["name"].lower() == status.lower()), None)
    if option is None:
        raise SystemExit(f"unknown status {status!r}; options: {[o['name'] for o in field['options']]}")
    graphql("""mutation($p:ID!,$i:ID!,$f:ID!,$o:String!){updateProjectV2ItemFieldValue(input:{
        projectId:$p,itemId:$i,fieldId:$f,value:{singleSelectOptionId:$o}}){clientMutationId}}""",
            p=p["id"], i=item_for_issue(p["id"], number), f=field["id"], o=option["id"])
    print(f"#{number} -> {option['name']}")


def ready() -> None:
    p = project()
    cursor = None
    while True:
        data = graphql("""query($id:ID!,$after:String){node(id:$id){... on ProjectV2{items(first:100,after:$after){
            pageInfo{hasNextPage endCursor}
            nodes{content{... on Issue{number title state}}
                  status:fieldValueByName(name:"Status"){... on ProjectV2ItemFieldSingleSelectValue{name}}}}}}}""",
                       id=p["id"], after=cursor)
        page = data["node"]["items"]
        for n in page["nodes"]:
            c = n["content"] or {}
            if (n["status"] or {}).get("name") == "Ready" and c.get("state") == "OPEN":
                print(f"#{c['number']}\t{c['title']}")
        if not page["pageInfo"]["hasNextPage"]:
            return
        cursor = page["pageInfo"]["endCursor"]


if __name__ == "__main__":
    if len(sys.argv) == 4 and sys.argv[1] == "status":
        set_status(int(sys.argv[2]), sys.argv[3])
    elif len(sys.argv) == 2 and sys.argv[1] == "ready":
        ready()
    else:
        raise SystemExit(__doc__)

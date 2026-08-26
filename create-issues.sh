#!/usr/bin/env bash
# Usage: ./create-issues.sh batch-issues.md
# Butuh: gh CLI udah login (gh auth login), dan gh auth status OK.

set -e

FILE="${1:-batch-issues.md}"

if [ ! -f "$FILE" ]; then
  echo "File $FILE gak ketemu."
  exit 1
fi

repo=""
title=""
labels=""
body=""
in_body=0
count=0

create_issue() {
  if [ -n "$repo" ] && [ -n "$title" ]; then
    echo "→ Creating issue di $repo: $title"
    if [ -n "$labels" ]; then
      gh issue create --repo "$repo" --title "$title" --label "$labels" --body "$body"
    else
      gh issue create --repo "$repo" --title "$title" --body "$body"
    fi
    count=$((count+1))
    sleep 1
  fi
}

while IFS= read -r line; do
  if [[ "$line" == "### REPO: "* ]]; then
    create_issue
    repo="${line#### REPO: }"
    title=""
    labels=""
    body=""
    in_body=0
  elif [[ "$line" == "TITLE: "* ]]; then
    title="${line#TITLE: }"
  elif [[ "$line" == "LABELS: "* ]]; then
    labels="${line#LABELS: }"
  elif [[ "$line" == "---" ]]; then
    if [ "$in_body" -eq 0 ]; then
      in_body=1
    else
      in_body=2
    fi
  elif [ "$in_body" -eq 1 ]; then
    body="${body}${line}
"
  fi
done < "$FILE"

# create last one
create_issue

echo ""
echo "Selesai. Total $count issue dibuat."
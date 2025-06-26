import random
import os
import sys


def load_real_usernames(filepath):
    """Load real usernames from the given file."""
    with open(filepath, "r") as f:
        return [line.strip() for line in f if line.strip()]


def create_fake_username(real_username):
    """Create a fake username based on a real one with small modifications."""
    fake_username = list(real_username)

    # Randomly decide on a modification type
    modification_type = random.choice(["replace", "shuffle", "append"])

    if modification_type == "replace":
        # Replace a character in the username
        idx = random.randint(0, len(fake_username) - 1)
        fake_username[idx] = random.choice("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")

    elif modification_type == "shuffle" and len(fake_username) > 1:
        # Shuffle two adjacent characters
        idx = random.randint(0, len(fake_username) - 2)
        fake_username[idx], fake_username[idx + 1] = fake_username[idx + 1], \
            fake_username[idx]

    elif modification_type == "append":
        # Append a random character at the end
        fake_username.append(random.choice("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"))

    return ''.join(fake_username)


def generate_fake_usernames(real_usernames, num_fakes=100):
    """Generate a specified number of fake usernames based on real usernames."""
    fake_usernames = set()

    while len(fake_usernames) < num_fakes:
        real_username = random.choice(real_usernames)
        fake_username = create_fake_username(real_username)
        fake_usernames.add(fake_username)

    return fake_usernames


# Load real usernames
real_user_file = "data/users.txt"
real_usernames = load_real_usernames(real_user_file)

# Generate fake usernames based on real usernames
fake_user_file = "data/fake_users.txt"
fake_usernames = generate_fake_usernames(real_usernames,
                                         num_fakes=int(sys.argv[1]))

# Save fake usernames to a file
os.makedirs("data", exist_ok=True)
with open(fake_user_file, "w") as f:
    for user in fake_usernames:
        f.write(user + "\n")

print(f"Generated {len(fake_usernames)} fake usernames based on real usernames.")

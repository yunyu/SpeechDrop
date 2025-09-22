<template>
    <div class="card main-card">
        <header>
            <h3>SpeechDrop</h3>
        </header>
        <div class="room-controls code-control">
            <div>
                <input
                    type="text"
                    v-model="code"
                    :class="codeInputClass"
                    placeholder="Type an existing room code"
                    maxlength="6"
                    autocorrect="off"
                    autocapitalize="none"
                >
                <div class="input-hint" v-html="hint"></div>
            </div>
        </div>
        <div class="sep-text">OR</div>
        <div class="room-controls make-room-control">
            <form class="room-form" @submit.prevent="makeRoom">
                <input
                    class="room-text"
                    type="text"
                    name="name"
                    pattern=".{1,60}"
                    maxlength="60"
                    required
                    placeholder="Type a new room name"
                >
                <input class="button" type="submit" value="Make Room">
            </form>
        </div>
    </div>
</template>

<script>
import Cookies from 'js-cookie';

const maxChars = 6;

const charRemaining = val => {
    const remaining = maxChars - val.length;
    return `${remaining} character${remaining !== 1 ? 's' : ''} left`;
};

export default {
    name: 'MainCard',
    data() {
        return {
            code: '',
            hint: charRemaining(''),
            codeInputClass: null
        };
    },
    watch: {
        code(val) {
            this.produceHint(val);
        }
    },
    methods: {
        makeRoom(e) {
            ga('send', 'event', 'Room', 'make');

            const token = Cookies.get('XSRF-TOKEN');
            const body = new FormData(e.target);

            fetch('/makeroom', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'X-XSRF-TOKEN': token },
                body
            })
                .then(resp => {
                    if (!resp.ok) throw new Error('CSRF failed');
                    if (resp.redirected) {
                        window.location.href = resp.url;
                        return;
                    }

                    window.location.href = resp.headers.get('Location') || '/';
                })
                .catch(() => {
                    // handle error/UI feedback
                });
        },
        produceHint(val) {
            if (val.length < maxChars) {
                this.codeInputClass = null;
                this.hint = charRemaining(val);
            } else {
                this.hint = 'Checking...';
                const r = new XMLHttpRequest();
                r.open('GET', `/${val}/index`, true);
                r.onload = () => {
                    if (r.status !== 200) {
                        this.codeInputClass = 'input-invalid';
                        this.hint = '<span style="color:#b72a2a">Invalid room code</span>';
                    } else {
                        window.location.href = `/${val}`;
                        this.hint = 'Taking you there...';
                    }
                };
                r.send();
            }
        }
    }
};
</script>

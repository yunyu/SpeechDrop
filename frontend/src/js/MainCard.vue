<template>
    <div class="card main-card">
        <header>
            <h3>SpeechDrop</h3>
        </header>
        <div class="room-controls code-control">
            <div>
                <input type="text" v-model="code" v-bind:class="codeInputClass" placeholder="Type an existing room code"
                       maxlength="6"
                       autocorrect="off" autocapitalize="none">
                <div class="input-hint" v-html="hint"></div>
            </div>
        </div>
        <div class="sep-text">OR</div>
        <div class="room-controls make-room-control">
            <form class="room-form" action="/makeroom" method="post" onsubmit="ga('send', 'event', 'Room', 'make')">
                <input type="hidden" name="X-XSRF-TOKEN" v-bind:value="csrfToken">
                <input class="room-text" type="text" name="name" pattern=".{1,60}" maxlength="60" required
                       placeholder="Type a new room name">
                <input class="button" type="submit" value="Make Room">
            </form>
        </div>
    </div>
</template>